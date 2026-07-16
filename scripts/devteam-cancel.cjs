#!/usr/bin/env node
const { cancelJob } = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-cancel.cjs <job-id> [options]

Cancel and archive a devteam job. Closes the PR if open.

Options:
  --json     Print result as JSON
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
		const result = cancelJob(process.cwd(), jobId);
		if (args.includes('--json')) {
			console.log(JSON.stringify(result, null, 2));
		} else {
			console.log(`Cancelled ${jobId}`);
			console.log(`Archived to ${result.archived.archivePath}`);
		}
		process.exit(0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
