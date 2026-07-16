#!/usr/bin/env node
const { archiveJob, readJobMeta } = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-archive.cjs <job-id> [options]

Archive a devteam job manually (fallback after GitHub merge).

Options:
  --force    Archive regardless of status
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
		console.error('Error: job-id is required.');
		printHelp();
		process.exit(1);
	}

	try {
		const meta = readJobMeta(process.cwd(), jobId);
		const result = archiveJob(process.cwd(), jobId, {
			force: args.includes('--force'),
			reason: meta?.status === 'cancelled' ? 'cancelled' : 'merged_on_github',
		});
		if (args.includes('--json')) {
			console.log(JSON.stringify(result, null, 2));
		} else {
			console.log(`Archived ${jobId} → ${result.archivePath}`);
		}
		process.exit(0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
