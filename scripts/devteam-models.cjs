#!/usr/bin/env node
const {
	formatDevteamModelsReport,
	getJobModels,
	validateDevteamAgentModels,
} = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-models.cjs [options]

Print devteam stage model slugs from devteam/model-presets.json.

Options:
  --job <job-id>        Include models frozen on a job (from meta.json)
  --validate-agents     Verify .cursor/agents/* frontmatter matches presets (exit 1 on drift)
  --json                Print structured JSON
  --help                Show this help

Examples:
  node scripts/devteam-models.cjs
  node scripts/devteam-models.cjs --job job-012
  node scripts/devteam-models.cjs --validate-agents
`);
}

function main() {
	const args = process.argv.slice(2);
	if (args.includes('--help') || args.includes('-h')) {
		printHelp();
		process.exit(0);
	}

	const jobIndex = args.indexOf('--job');
	const jobId = jobIndex >= 0 ? args[jobIndex + 1] : null;
	const validateAgents = args.includes('--validate-agents');
	const json = args.includes('--json');
	const root = process.cwd();

	try {
		if (validateAgents) {
			const result = validateDevteamAgentModels(root);
			if (json) {
				console.log(JSON.stringify(result, null, 2));
			} else if (result.valid) {
				console.log('Devteam agent frontmatter matches model-presets.json.');
				console.log('');
				console.log(formatDevteamModelsReport(root, jobId));
			} else {
				console.error('Devteam agent model drift detected:');
				for (const error of result.errors) {
					console.error(`  - ${error}`);
				}
				console.error('');
				console.error('Fix .cursor/agents/*.md frontmatter or update devteam/model-presets.json.');
			}
			process.exit(result.valid ? 0 : 1);
		}

		if (json) {
			const models = jobId ? getJobModels(root, jobId) : validateDevteamAgentModels(root).presets;
			console.log(JSON.stringify({ jobId, models }, null, 2));
			process.exit(0);
		}

		console.log(formatDevteamModelsReport(root, jobId));
		process.exit(0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
