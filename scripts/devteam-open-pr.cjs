#!/usr/bin/env node
const { openPrForJob } = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-open-pr.cjs <job-id> [options]

Create or find draft PR and set meta.pr / chatTitle (devteam pr###).

Options:
  --no-push   Skip git push before PR create
  --json      Print result as JSON
  --help      Show this help
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
		console.error('Error: job-id is required.');
		printHelp();
		process.exit(1);
	}

	try {
		const result = openPrForJob(process.cwd(), jobId, { push: !args.includes('--no-push') });
		if (args.includes('--json')) {
			console.log(JSON.stringify(result, null, 2));
		} else {
			console.log(`PR for ${jobId}: ${result.pr}`);
			if (result.chatTitle) {
				console.log(`Rename chat to: ${result.chatTitle}`);
			}
		}
		process.exit(0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
