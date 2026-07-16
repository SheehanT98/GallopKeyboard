#!/usr/bin/env node
/**
 * Bundle individual plans/NNN-*.md files into plans/phases/phase-NN.md
 * for devteam:queue-phases (one phase = one PR).
 */
const fs = require('fs');
const path = require('path');

const root = process.cwd();
const manifestPath = path.join(root, 'plans/phases/manifest.json');
const plansDir = path.join(root, 'plans');

function findPlanFile(planId) {
	const padded = String(planId).padStart(3, '0');
	const entries = fs.readdirSync(plansDir);
	const match = entries.find(
		(name) => name.startsWith(`${padded}-`) && name.endsWith('.md') && name !== 'README.md',
	);
	if (!match) {
		throw new Error(`No plan file found for id ${planId} (expected ${padded}-*.md in plans/)`);
	}
	return path.join(plansDir, match);
}

function main() {
	if (!fs.existsSync(manifestPath)) {
		console.error('Error: plans/phases/manifest.json not found');
		process.exit(1);
	}

	const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
	const outDir = path.join(root, 'plans/phases');
	fs.mkdirSync(outDir, { recursive: true });

	let count = 0;
	for (const phase of manifest.phases) {
		const planIds = phase.planIds || [];
		const parts = [
			`# ${phase.feature}`,
			'',
			`> Bundled phase plan (${phase.id}). Execute sub-plans **in order** on one branch.`,
			'',
		];

		for (const planId of planIds) {
			const planPath = findPlanFile(planId);
			const body = fs.readFileSync(planPath, 'utf8');
			parts.push('---', '', `<!-- from ${path.relative(root, planPath)} -->`, '', body, '');
		}

		const outPath = path.join(outDir, `${phase.id.replace('phase-', 'phase-')}.md`);
		const relOut = phase.file || `plans/phases/${path.basename(outPath)}`;
		const finalPath = path.join(root, relOut);
		fs.mkdirSync(path.dirname(finalPath), { recursive: true });
		fs.writeFileSync(finalPath, parts.join('\n'), 'utf8');
		console.log(`Wrote ${relOut} (${planIds.length} plan(s))`);
		count += 1;
	}

	console.log(`Done — ${count} phase plan(s).`);
}

main();
