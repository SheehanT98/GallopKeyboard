#!/usr/bin/env node
const { reviseJob } = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-revise.cjs <job-id> "<notes>" [options]

Send a completed job back for revision with your notes.

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
	const notesParts = args.filter(
		(arg) => arg !== jobId && !arg.startsWith('--') && arg !== '-h',
	);
	const notes = notesParts.join(' ').trim();

	if (!jobId || !notes) {
		console.error('Error: job-id and revision notes are required.');
		printHelp();
		process.exit(1);
	}

	try {
		const result = reviseJob(process.cwd(), jobId, notes);
		if (args.includes('--json')) {
			console.log(JSON.stringify(result, null, 2));
		} else {
			console.log(`Revising ${jobId} — status: ${result.status}`);
			console.log('Re-run code → test → review → double_checking in this chat.');
		}
		process.exit(0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
