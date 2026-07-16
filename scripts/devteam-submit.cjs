#!/usr/bin/env node
const { submitJob } = require('./devteam-lib.cjs');

function printHelp() {
	console.log(`Usage: node scripts/devteam-submit.cjs "<feature or task>" [options]

Submit a devteam v2 job. Uses models from devteam/model-presets.json (no per-run prompt).

Options:
  --quick --plan <path>   Quick mode: implement an existing plan (skip planning stage)
  --depends-on <job-id>   Wait until dependency job is merged before starting (repeatable)
  --json                  Print result as JSON
  --help                  Show this help

Examples:
  node scripts/devteam-submit.cjs "Reskin mobile task details to match Legacy"
  node scripts/devteam-submit.cjs "Implement plan 017" --quick --plan plans/017-client-form.md
  node scripts/devteam-submit.cjs "Implement plan 040" --quick --plan plans/040.md --depends-on job-039
`);
}

function main() {
	const args = process.argv.slice(2);
	if (args.includes('--help') || args.includes('-h')) {
		printHelp();
		process.exit(0);
	}

	const quick = args.includes('--quick');
	const planIndex = args.indexOf('--plan');
	const planRef = planIndex >= 0 ? args[planIndex + 1] : null;
	const json = args.includes('--json');
	const dependsOn = args
		.map((arg, index) => (arg === '--depends-on' ? args[index + 1] : null))
		.filter((value) => value && /^job-\d{3}$/.test(value));

	const featureParts = [];
	for (let i = 0; i < args.length; i++) {
		const arg = args[i];
		if (arg === '--quick' || arg === '--json' || arg === '--help' || arg === '-h') {
			continue;
		}
		if (arg === '--plan' || arg === '--depends-on') {
			i += 1;
			continue;
		}
		if (arg.startsWith('--')) {
			console.error(`Unknown option: ${arg}`);
			process.exit(1);
		}
		featureParts.push(arg);
	}

	const feature = featureParts.join(' ').trim();
	if (!feature) {
		console.error('Error: feature description is required.');
		printHelp();
		process.exit(1);
	}

	if (quick && !planRef) {
		console.error('Error: --quick requires --plan <path>');
		process.exit(1);
	}

	try {
		const result = submitJob(process.cwd(), feature, {
			mode: quick ? 'quick' : 'full',
			planRef,
			originalAsk: feature,
			dependsOn,
		});

		if (json) {
			console.log(JSON.stringify(result, null, 2));
		} else {
			console.log(`Submitted ${result.jobId} — status: ${result.meta.status}`);
			console.log(`Branch: ${result.meta.branch}`);
			if (result.meta.models) {
				console.log('Models (from devteam/model-presets.json):');
				console.log(`  plan: ${result.meta.models.plan}`);
				console.log(`  code: ${result.meta.models.code}`);
				console.log(`  test: ${result.meta.models.test}`);
				console.log(`  review: ${result.meta.models.review}`);
				console.log(`  doubleCheck: ${result.meta.models.doubleCheck}`);
			}
			if (result.meta.chatTitle) {
				console.log(`Chat title: ${result.meta.chatTitle}`);
			}
			console.log('');
			console.log('Run the pipeline in this chat until awaiting_review, then paste 04-review.md.');
			console.log('Orchestrator: pass `model` on every devteam stage — see devteam/MODEL-POLICY.md.');
		}
		process.exit(0);
	} catch (error) {
		console.error(`Error: ${error.message}`);
		process.exit(1);
	}
}

main();
