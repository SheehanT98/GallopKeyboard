#!/usr/bin/env node
const { loadAllJobs, analyzeConflicts } = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-conflicts.cjs [options]

Check file overlap between active devteam jobs.

Options:
  --json     Print findings as JSON
  --help     Show this help
`);
}

function main() {
	const args = process.argv.slice(2);
	if (args.includes('--help') || args.includes('-h')) {
		printHelp();
		process.exit(0);
	}

	const root = process.cwd();
	const { jobs } = loadAllJobs(root);
	const findings = analyzeConflicts(jobs, { root });
	const holds = findings.filter((f) => f.action === 'hold');

	if (args.includes('--json')) {
		console.log(JSON.stringify({ findings, holds }, null, 2));
		process.exit(holds.length ? 1 : 0);
	}

	if (findings.length === 0) {
		console.log('No file overlaps between active jobs.');
		process.exit(0);
	}

	for (const finding of findings) {
		console.log(
			`${finding.jobA} ↔ ${finding.jobB}: ${finding.action} (${finding.overlap.join(', ')})`,
		);
	}
	process.exit(holds.length ? 1 : 0);
}

main();
