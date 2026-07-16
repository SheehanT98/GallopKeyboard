#!/usr/bin/env node
const { validateJob, loadAllJobs, validateDevteamAgentModels } = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-validate.cjs [job-id] [options]

Validate devteam job meta and artifacts.

Options:
  --stage <name>   plan | code | test | review | double_checking
  --json           Print as JSON
  --help           Show this help
`);
}

function main() {
	const args = process.argv.slice(2);
	if (args.includes('--help') || args.includes('-h')) {
		printHelp();
		process.exit(0);
	}

	const stageIndex = args.indexOf('--stage');
	const stage = stageIndex >= 0 ? args[stageIndex + 1] : null;
	const jobId = args.find((arg) => /^job-\d{3}$/.test(arg));

	try {
		const agentCheck = validateDevteamAgentModels(process.cwd());
		if (!agentCheck.valid) {
			if (args.includes('--json')) {
				console.log(JSON.stringify({ agentModels: agentCheck }, null, 2));
			} else {
				console.error('Devteam agent models invalid (see devteam/MODEL-POLICY.md):');
				for (const error of agentCheck.errors) {
					console.error(`  - ${error}`);
				}
			}
			process.exit(1);
		}

		if (jobId) {
			const result = validateJob(process.cwd(), jobId, { stage });
			if (args.includes('--json')) {
				console.log(JSON.stringify(result, null, 2));
			} else if (result.valid) {
				console.log(`Job ${jobId} is valid${stage ? ` (stage: ${stage})` : ''}.`);
			} else {
				console.error(`Job ${jobId} invalid:`);
				for (const error of result.errors) {
					console.error(`  - ${error}`);
				}
			}
			process.exit(result.valid ? 0 : 1);
		}

		const { jobs } = loadAllJobs(process.cwd());
		const results = Object.keys(jobs).map((id) => validateJob(process.cwd(), id, { stage }));
		const invalid = results.filter((r) => !r.valid);
		if (args.includes('--json')) {
			console.log(JSON.stringify(results, null, 2));
		} else if (invalid.length === 0) {
			console.log(`All ${results.length} jobs valid.`);
		} else {
			for (const result of invalid) {
				console.error(`${result.jobId}: ${result.errors.join('; ')}`);
			}
		}
		process.exit(invalid.length ? 1 : 0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
