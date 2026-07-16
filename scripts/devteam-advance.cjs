#!/usr/bin/env node
const {
	getAdvanceChecks,
	advanceJob,
	recordTestFailure,
	recordDoubleCheckFailure,
} = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-advance.cjs <job-id> --to <stage> [options]

Run stage gate checks and update job meta.

Stages: coding | testing | reviewing | double_checking | awaiting_review

Options:
  --dry-run           Print checks without mutating meta
  --skip-notify       Skip GitHub PR comment on awaiting_review
  --test-failed       Record a test failure (loops back to coding)
  --double-check-failed  Record double-check failure (loop or handoff)
  --no-ensure-pr      Do not auto-run open-pr when advancing to testing
  --json              Print result as JSON
  --help              Show this help
`);
}

function main() {
	const args = process.argv.slice(2);
	if (args.includes('--help') || args.includes('-h')) {
		printHelp();
		process.exit(0);
	}

	const jobId = args.find((arg) => /^job-\d{3}$/.test(arg));
	const toIndex = args.indexOf('--to');
	const targetStage = toIndex >= 0 ? args[toIndex + 1] : null;

	if (!jobId || !targetStage) {
		console.error('Error: job-id and --to <stage> are required.');
		printHelp();
		process.exit(1);
	}

	const options = {
		dryRun: args.includes('--dry-run'),
		skipNotify: args.includes('--skip-notify'),
		ensurePr: !args.includes('--no-ensure-pr'),
	};

	try {
		if (args.includes('--test-failed')) {
			const result = recordTestFailure(process.cwd(), jobId);
			console.log(JSON.stringify(result, null, 2));
			process.exit(result.action === 'blocked' ? 1 : 0);
		}

		if (args.includes('--double-check-failed')) {
			const result = recordDoubleCheckFailure(process.cwd(), jobId);
			console.log(JSON.stringify(result, null, 2));
			process.exit(0);
		}

		if (options.dryRun) {
			const report = getAdvanceChecks(process.cwd(), jobId, targetStage);
			if (args.includes('--json')) {
				console.log(JSON.stringify({ ...report, dryRun: true }, null, 2));
			} else {
				console.log(`Dry run: advance ${jobId} → ${targetStage}`);
				for (const check of report.checks) {
					console.log(`  check: ${check}`);
				}
				if (report.blockers.length) {
					console.error('Blockers:');
					for (const blocker of report.blockers) {
						console.error(`  - ${blocker}`);
					}
					process.exit(1);
				}
			}
			process.exit(report.blockers.length ? 1 : 0);
		}

		const result = advanceJob(process.cwd(), jobId, targetStage, options);
		if (args.includes('--json')) {
			console.log(JSON.stringify(result, null, 2));
		} else {
			console.log(`Advanced ${result.jobId} → ${result.targetStage}`);
			if (result.notifyWarning) {
				console.error(`Warning: notify failed — ${result.notifyWarning}`);
			}
			if (result.meta?.chatTitle) {
				console.log(`Chat title: ${result.meta.chatTitle}`);
			}
		}
		process.exit(0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
