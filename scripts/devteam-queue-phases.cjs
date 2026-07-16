#!/usr/bin/env node
const fs = require('fs');
const path = require('path');
const {
	submitJob,
	cancelJob,
	readJobMeta,
	loadAllJobs,
	releaseConflictHolds,
} = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-queue-phases.cjs [options]

Submit phased devteam jobs from plans/phases/manifest.json.
One phase = one devteam job = one PR. Jobs auto-promote when dependencies merge.

Options:
  --dry-run              Print submissions without creating jobs
  --resume               Skip completedPhases; cancel stale phased jobs first
  --cancel-stale         With --resume: archive conflict_hold phased jobs
  --cancel-job-018       Cancel legacy single-plan job-018 before queueing
  --json                 Print result as JSON
  --help                 Show this help

After queueing:
  npm run devteam:status -- --fetch
  npm run devteam:conflicts

Orchestration guide: devteam/PHASE-ORCHESTRATION.md
`);
}

function cancelStalePhasedJobs(root, manifest, dryRun) {
	const completed = new Set(manifest.completedPhases || []);
	const stale = [];
	const { jobs } = loadAllJobs(root);

	for (const [jobId, meta] of Object.entries(jobs)) {
		if (!meta.planRef?.startsWith('plans/phases/')) {
			continue;
		}
		const phaseId = manifest.phases.find((phase) => phase.file === meta.planRef)?.id;
		if (phaseId && completed.has(phaseId)) {
			continue;
		}
		if (['conflict_hold', 'queued', 'coding', 'planning'].includes(meta.status)) {
			stale.push({ jobId, status: meta.status, phaseId: phaseId || meta.planRef });
		}
	}

	for (const row of stale) {
		if (dryRun) {
			console.log(`Would cancel stale ${row.jobId} (${row.phaseId || 'unknown'}, ${row.status})`);
			continue;
		}
		try {
			cancelJob(root, row.jobId);
			console.log(`Cancelled stale ${row.jobId} (${row.phaseId || 'unknown'}).`);
		} catch (error) {
			console.warn(`Warning: could not cancel ${row.jobId}: ${error.message}`);
		}
	}

	return stale;
}

function main() {
	const args = process.argv.slice(2);
	if (args.includes('--help') || args.includes('-h')) {
		printHelp();
		process.exit(0);
	}

	const dryRun = args.includes('--dry-run');
	const resume = args.includes('--resume');
	const cancelStale = args.includes('--cancel-stale') || resume;
	const cancel018 = args.includes('--cancel-job-018');
	const json = args.includes('--json');
	const root = process.cwd();
	const manifestPath = path.join(root, 'plans/phases/manifest.json');

	if (!fs.existsSync(manifestPath)) {
		console.error('Error: plans/phases/manifest.json not found. Run on a branch with phase orchestration.');
		process.exit(1);
	}

	const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
	const completedPhases = new Set(manifest.completedPhases || []);
	const jobByPhase = { ...(manifest.completedJobs || {}) };
	const results = [];

	if (cancelStale) {
		cancelStalePhasedJobs(root, manifest, dryRun);
	}

	if (cancel018 && !dryRun) {
		const meta = readJobMeta(root, 'job-018');
		if (meta && meta.status !== 'cancelled' && meta.status !== 'archived') {
			try {
				cancelJob(root, 'job-018');
				console.log('Cancelled job-018 (superseded by phased queue).');
			} catch (error) {
				console.warn(`Warning: could not cancel job-018: ${error.message}`);
			}
		}
	}

	const phasesToSubmit = manifest.phases.filter((phase) => !completedPhases.has(phase.id));

	for (const phase of phasesToSubmit) {
		const planPath = path.join(root, phase.file);
		if (!fs.existsSync(planPath)) {
			console.error(`Error: missing phase plan ${phase.file}. Run: node scripts/generate-phase-plans.cjs`);
			process.exit(1);
		}

		const dependsOn = [
			...(phase.dependsOnJobs || []),
			...(phase.dependsOnPhases || []).map((phaseId) => jobByPhase[phaseId]).filter(Boolean),
		];

		const missingPhaseDeps = (phase.dependsOnPhases || []).filter((phaseId) => !jobByPhase[phaseId]);
		if (missingPhaseDeps.length > 0) {
			console.error(
				`Error: phase ${phase.id} depends on unresolved phases: ${missingPhaseDeps.join(', ')}`,
			);
			process.exit(1);
		}

		if (dryRun) {
			results.push({ phaseId: phase.id, feature: phase.feature, planRef: phase.file, dependsOn });
			continue;
		}

		const result = submitJob(root, phase.feature, {
			mode: 'quick',
			planRef: phase.file,
			originalAsk: phase.feature,
			dependsOn,
		});

		jobByPhase[phase.id] = result.jobId;
		results.push({
			phaseId: phase.id,
			jobId: result.jobId,
			status: result.meta.status,
			branch: result.meta.branch,
			dependsOn,
			feature: phase.feature,
		});
	}

	const outPath = path.join(root, 'devteam/phase-queue.json');
	if (!dryRun) {
		fs.writeFileSync(
			outPath,
			JSON.stringify(
				{
					queuedAt: new Date().toISOString(),
					resume,
					completedPhases: [...completedPhases],
					jobByPhase,
					results,
				},
				null,
				2,
			),
		);
		releaseConflictHolds(root);
	}

	if (json) {
		console.log(JSON.stringify({ jobByPhase, results, completedPhases: [...completedPhases] }, null, 2));
	} else if (dryRun) {
		console.log(`Dry run — would submit ${phasesToSubmit.length} phases (skipping ${completedPhases.size} done):`);
		for (const row of results) {
			console.log(`  ${row.phaseId} → depends [${row.dependsOn.join(', ')}]`);
		}
	} else {
		console.log(
			`Queued ${results.length} phased jobs (${completedPhases.size} already complete). Mapping: devteam/phase-queue.json`,
		);
		console.log('');
		for (const row of results) {
			console.log(`${row.phaseId} → ${row.jobId} (${row.status})`);
		}
		const { jobs } = loadAllJobs(root);
		const active = Object.values(jobs).filter((m) =>
			['planning', 'coding', 'testing', 'reviewing', 'double_checking'].includes(m.status),
		);
		console.log('');
		console.log(`Active now: ${active.length}/3`);
		console.log('Run pipelines for coding jobs — see devteam/PHASE-ORCHESTRATION.md');
	}

	process.exit(0);
}

main();
