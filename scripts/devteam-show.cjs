#!/usr/bin/env node
const { buildShowReport, formatShowReport } = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-show.cjs <job-id> [options]

Print artifact walkthrough for a devteam job (01-plan through 05-double-check).

Options:
  --json     Print structured report as JSON
  --help     Show this help
`);
}

function main() {
	const args = process.argv.slice(2);
	if (args.includes('--help') || args.includes('-h')) {
		printHelp();
		process.exit(0);
	}

	const jobId = args.find((arg) => /^job-\d{3}$/.test(arg));
	if (!jobId) {
		console.error('Error: job-id (e.g. job-001) is required.');
		printHelp();
		process.exit(1);
	}

	try {
		const report = buildShowReport(process.cwd(), jobId);
		if (args.includes('--json')) {
			console.log(JSON.stringify(report, null, 2));
		} else {
			console.log(formatShowReport(report));
		}
		process.exit(0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
