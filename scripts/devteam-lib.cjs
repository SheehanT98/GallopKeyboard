#!/usr/bin/env node
/**
 * Devteam v2 — job queue, soft cap of 3 active agent jobs, conflict hold,
 * approve / revise / cancel.
 */
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const ACTIVE_CAP = 3;
const MAX_TEST_RETRIES = 5;
const MAX_DOUBLE_CHECK_RETRIES = 3;
const DEFAULT_BRANCH = process.env.DEVTEAM_BASE_BRANCH || 'main';

const VALID_STATUSES = new Set([
	'queued',
	'conflict_hold',
	'planning',
	'coding',
	'testing',
	'reviewing',
	'double_checking',
	'revising',
	'awaiting_review',
	'approved_pending_ci',
	'approved',
	'cancelled',
	'blocked',
]);

const ACTIVE_AGENT_STATUSES = new Set([
	'planning',
	'coding',
	'testing',
	'reviewing',
	'revising',
]);

const WAIT_STATUSES = new Set(['queued', 'conflict_hold']);

const TERMINAL_STATUSES = new Set(['approved', 'cancelled', 'blocked']);

const ARTIFACTS = [
	{ key: 'plan', file: '01-plan.md' },
	{ key: 'code', file: '02-code-summary.md' },
	{ key: 'test', file: '03-test-report.md' },
	{ key: 'review', file: '04-review.md' },
	{ key: 'doubleCheck', file: '05-double-check.md' },
];

const ADVANCE_TARGETS = new Set([
	'coding',
	'testing',
	'reviewing',
	'double_checking',
	'awaiting_review',
]);

/** Preset keys and stage routing — keep in sync with devteam/MODEL-POLICY.md */
const DEVTEAM_MODEL_PRESET_KEYS = ['plan', 'code', 'test', 'review', 'doubleCheck'];

const DEVTEAM_STAGE_AGENT_PATHS = {
	plan: '.cursor/agents/planner.md',
	code: '.cursor/agents/coder.md',
	test: '.cursor/agents/tester.md',
	review: '.cursor/agents/reviewer.md',
	doubleCheck: '.cursor/agents/double-checker.md',
};

const DEVTEAM_STAGE_SUBAGENT_TYPES = {
	plan: 'devteam-planner',
	code: 'devteam-coder',
	test: 'devteam-tester',
	review: 'devteam-reviewer',
	doubleCheck: 'devteam-double-checker',
};

function getRepoRoot(cwd = process.cwd()) {
	return cwd;
}

function getDevteamPaths(root) {
	const devteamRoot = path.join(root, 'devteam');
	return {
		devteamRoot,
		jobsDir: path.join(devteamRoot, 'jobs'),
		archiveDir: path.join(devteamRoot, 'archive'),
		readmePath: path.join(devteamRoot, 'README.md'),
		templatesDir: path.join(devteamRoot, 'templates'),
		modelPresetsPath: path.join(devteamRoot, 'model-presets.json'),
		registryPath: path.join(devteamRoot, 'registry.json'),
		hotFilesPath: path.join(devteamRoot, 'hot-files.json'),
	};
}

function slugify(text) {
	return (
		String(text || 'feature')
			.toLowerCase()
			.replace(/[^a-z0-9]+/g, '-')
			.replace(/^-+|-+$/g, '')
			.slice(0, 48) || 'feature'
	);
}

function readJsonFile(filePath) {
	if (!fs.existsSync(filePath)) {
		return null;
	}
	try {
		return JSON.parse(fs.readFileSync(filePath, 'utf8'));
	} catch {
		return null;
	}
}

function writeJsonFile(filePath, data) {
	fs.mkdirSync(path.dirname(filePath), { recursive: true });
	fs.writeFileSync(filePath, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
}

function jobDir(root, jobId) {
	return path.join(getDevteamPaths(root).jobsDir, jobId);
}

function metaFilePath(root, jobId) {
	return path.join(jobDir(root, jobId), 'meta.json');
}

function artifactPath(root, jobId, filename) {
	return path.join(jobDir(root, jobId), filename);
}

function readJobMeta(root, jobId) {
	return readJsonFile(metaFilePath(root, jobId));
}

function writeJobMeta(root, jobId, patch) {
	const metaPath = metaFilePath(root, jobId);
	const existing = readJsonFile(metaPath);
	if (!existing) {
		throw new Error(`Job ${jobId} has no meta.json`);
	}
	const updated = {
		...existing,
		...patch,
		lastStatusAt: patch.lastStatusAt ?? new Date().toISOString(),
	};
	writeJsonFile(metaPath, updated);
	updateReadmeDashboard(root);
	return updated;
}

function loadRegistry(root) {
	const { registryPath } = getDevteamPaths(root);
	const registry = readJsonFile(registryPath) || { nextJobNumber: 1, queue: [] };
	if (!Array.isArray(registry.queue)) {
		registry.queue = [];
	}
	if (!registry.nextJobNumber) {
		registry.nextJobNumber = 1;
	}
	return registry;
}

function saveRegistry(root, registry) {
	writeJsonFile(getDevteamPaths(root).registryPath, registry);
}

function formatJobId(number) {
	return `job-${String(number).padStart(3, '0')}`;
}

function parseJobIdFromBranch(branch) {
	const normalized = String(branch || '').replace(/^origin\//, '');
	const match = normalized.match(/^cursor\/devteam-(job-\d{3})-.+-c1fc$/);
	return match ? match[1] : null;
}

function listJobIds(root) {
	const { jobsDir } = getDevteamPaths(root);
	if (!fs.existsSync(jobsDir)) {
		return [];
	}
	return fs
		.readdirSync(jobsDir, { withFileTypes: true })
		.filter((entry) => entry.isDirectory() && /^job-\d{3}$/.test(entry.name))
		.map((entry) => entry.name)
		.sort();
}

function loadAllJobs(root = process.cwd()) {
	const jobs = {};
	for (const jobId of listJobIds(root)) {
		const meta = readJobMeta(root, jobId);
		if (meta) {
			jobs[jobId] = normalizeMeta(meta);
		}
	}
	const registry = loadRegistry(root);
	return { jobs, registry };
}

function normalizeMeta(meta) {
	return {
		...meta,
		id: meta.id,
		status: meta.status || 'queued',
		mode: meta.mode || 'full',
		originalAsk: meta.originalAsk ?? meta.feature ?? null,
		planRef: meta.planRef ?? null,
		feature: meta.feature ?? null,
		slug: meta.slug ?? slugify(meta.feature),
		branch: meta.branch ?? null,
		pr: meta.pr ?? null,
		prNumber: meta.prNumber ?? null,
		chatTitle: meta.chatTitle ?? null,
		plannedFiles: Array.isArray(meta.plannedFiles) ? meta.plannedFiles : [],
		touchedFiles: Array.isArray(meta.touchedFiles) ? meta.touchedFiles : [],
		blockedBy: Array.isArray(meta.blockedBy) ? meta.blockedBy : [],
		revisionNotes: Array.isArray(meta.revisionNotes) ? meta.revisionNotes : [],
		retryCounts: {
			test: meta.retryCounts?.test ?? 0,
		},
		models: meta.models ?? {},
		stages: meta.stages ?? {},
		startedAt: meta.startedAt ?? null,
		completedAt: meta.completedAt ?? null,
		lastStatusAt: meta.lastStatusAt ?? null,
		notifiedAt: meta.notifiedAt ?? null,
	};
}

function countActiveAgentJobs(jobs) {
	return Object.values(jobs).filter((meta) => ACTIVE_AGENT_STATUSES.has(meta.status)).length;
}

function loadModelPresets(root) {
	const { modelPresetsPath } = getDevteamPaths(root);
	const presets = readJsonFile(modelPresetsPath);
	if (!presets) {
		throw new Error(`Missing model presets at ${modelPresetsPath}`);
	}
	for (const key of DEVTEAM_MODEL_PRESET_KEYS) {
		if (!presets[key] || typeof presets[key] !== 'string') {
			throw new Error(`model-presets.json missing required key "${key}"`);
		}
	}
	return presets;
}

function buildModelsFromPresets(presets) {
	return {
		plan: presets.plan,
		code: presets.code,
		test: presets.test,
		review: presets.review,
		doubleCheck: presets.doubleCheck,
	};
}

function getJobModels(root, jobId) {
	const meta = readJobMeta(root, jobId);
	if (meta?.models && DEVTEAM_MODEL_PRESET_KEYS.every((key) => meta.models[key])) {
		return meta.models;
	}
	return buildModelsFromPresets(loadModelPresets(root));
}

function parseAgentFrontmatterModel(agentFilePath) {
	if (!fs.existsSync(agentFilePath)) {
		return null;
	}
	const content = fs.readFileSync(agentFilePath, 'utf8');
	const match = content.match(/^---\r?\n([\s\S]*?)\r?\n---/);
	if (!match) {
		return null;
	}
	const modelLine = match[1].match(/^model:\s*(.+)$/m);
	return modelLine ? modelLine[1].trim() : null;
}

function validateDevteamAgentModels(root) {
	const presets = loadModelPresets(root);
	const errors = [];
	const details = [];

	for (const key of DEVTEAM_MODEL_PRESET_KEYS) {
		const relativePath = DEVTEAM_STAGE_AGENT_PATHS[key];
		const agentPath = path.join(root, relativePath);
		const expected = presets[key];
		const actual = parseAgentFrontmatterModel(agentPath);

		details.push({
			stage: key,
			subagentType: DEVTEAM_STAGE_SUBAGENT_TYPES[key],
			agentPath: relativePath,
			expectedModel: expected,
			actualModel: actual,
		});

		if (!actual) {
			errors.push(`${relativePath}: missing frontmatter model:`);
			continue;
		}
		if (actual === 'inherit') {
			errors.push(`${relativePath}: model is "inherit" — must be "${expected}" from model-presets.json`);
			continue;
		}
		if (actual !== expected) {
			errors.push(`${relativePath}: model "${actual}" does not match model-presets.json "${expected}"`);
		}
	}

	return {
		valid: errors.length === 0,
		errors,
		details,
		presets: buildModelsFromPresets(presets),
	};
}

function formatDevteamModelsReport(root, jobId = null) {
	const presets = buildModelsFromPresets(loadModelPresets(root));
	const jobModels = jobId ? getJobModels(root, jobId) : null;
	const lines = [
		'Devteam stage models (from devteam/model-presets.json):',
		'',
		'| Stage | Subagent | Model |',
		'| --- | --- | --- |',
	];

	for (const key of DEVTEAM_MODEL_PRESET_KEYS) {
		const slug = jobModels?.[key] ?? presets[key];
		lines.push(`| ${key} | ${DEVTEAM_STAGE_SUBAGENT_TYPES[key]} | \`${slug}\` |`);
	}

	if (jobId) {
		lines.push('', `Job: ${jobId}`);
	}

	lines.push('', 'Orchestrator: pass `model` on every devteam Task launch. See devteam/MODEL-POLICY.md.');
	return lines.join('\n');
}

function createInitialMeta(jobId, feature, options = {}) {
	const slug = slugify(feature);
	const startedAt = new Date().toISOString();
	const mode = options.mode || 'full';
	const initialStatus = mode === 'quick' ? 'coding' : 'planning';
	return {
		id: jobId,
		status: initialStatus,
		mode,
		originalAsk: options.originalAsk || feature,
		planRef: options.planRef || null,
		feature,
		slug,
		branch: `cursor/devteam-${jobId}-${slug}-c1fc`,
		pr: null,
		prNumber: null,
		chatTitle: null,
		models: options.models,
		startedAt,
		lastStatusAt: startedAt,
		completedAt: null,
		plannedFiles: [],
		touchedFiles: [],
		blockedBy: [],
		dependsOn: options.dependsOn || [],
		revisionNotes: [],
		retryCounts: { test: 0, doubleCheck: 0 },
		notifiedAt: null,
		stages: {
			plan: mode === 'quick' ? 'skipped' : 'in_progress',
			code: mode === 'quick' ? 'in_progress' : 'pending',
			test: 'pending',
			review: 'pending',
			doubleCheck: 'pending',
		},
	};
}

function applyTemplate(template, values) {
	return template.replace(/\{\{([A-Z0-9_]+)\}\}/g, (_, key) => values[key] ?? '');
}

function readTemplate(root, filename) {
	const { templatesDir } = getDevteamPaths(root);
	const templatePath = path.join(templatesDir, filename);
	if (!fs.existsSync(templatePath)) {
		return null;
	}
	return fs.readFileSync(templatePath, 'utf8');
}

function fileOverlap(filesA, filesB) {
	const setB = new Set(filesB);
	return filesA.filter((file) => setB.has(file));
}

function loadHotFiles(root) {
	const { hotFilesPath } = getDevteamPaths(root);
	const data = readJsonFile(hotFilesPath);
	return Array.isArray(data?.files) ? data.files : [];
}

function getJobFiles(meta) {
	return [...(meta.plannedFiles || []), ...(meta.touchedFiles || [])];
}

function analyzeConflicts(jobsInput, options = {}) {
	const jobs = jobsInput || loadAllJobs(options.root || process.cwd()).jobs;
	const hotFiles = loadHotFiles(options.root || process.cwd());
	const findings = [];
	const jobIds = Object.keys(jobs);

	for (let i = 0; i < jobIds.length; i++) {
		for (let j = i + 1; j < jobIds.length; j++) {
			const idA = jobIds[i];
			const idB = jobIds[j];
			const metaA = jobs[idA];
			const metaB = jobs[idB];
			if (TERMINAL_STATUSES.has(metaA.status) || TERMINAL_STATUSES.has(metaB.status)) {
				continue;
			}

			const overlap = fileOverlap(getJobFiles(metaA), getJobFiles(metaB));
			if (overlap.length === 0) {
				continue;
			}

			const aActive = ACTIVE_AGENT_STATUSES.has(metaA.status);
			const bActive = ACTIVE_AGENT_STATUSES.has(metaB.status);
			let action = 'note_only';
			if (aActive && bActive) {
				action = 'hold';
			} else if (aActive || bActive) {
				action = 'hold';
			}

			findings.push({
				jobA: idA,
				jobB: idB,
				action,
				overlap,
				statusA: metaA.status,
				statusB: metaB.status,
				severity: overlap.some((file) => hotFiles.includes(file)) ? 'high' : 'normal',
			});
		}
	}

	return findings;
}

function findConflictBlockers(root, jobId, jobs) {
	const meta = jobs[jobId];
	if (!meta) {
		return [];
	}
	const blockers = [];
	const files = getJobFiles(meta);
	if (files.length === 0) {
		return blockers;
	}
	const hotFiles = new Set(loadHotFiles(root));

	for (const [otherId, otherMeta] of Object.entries(jobs)) {
		if (otherId === jobId || TERMINAL_STATUSES.has(otherMeta.status)) {
			continue;
		}
		if (!ACTIVE_AGENT_STATUSES.has(otherMeta.status)) {
			continue;
		}
		const overlap = fileOverlap(files, getJobFiles(otherMeta)).filter((file) => !hotFiles.has(file));
		if (overlap.length > 0) {
			blockers.push({ jobId: otherId, status: otherMeta.status, overlap });
		}
	}
	return blockers;
}

function isJobArchived(root, jobId) {
	const { archiveDir } = getDevteamPaths(root);
	if (!fs.existsSync(archiveDir)) {
		return false;
	}
	return fs.readdirSync(archiveDir).some((name) => name.startsWith(`${jobId}-`));
}

function isDependencySatisfied(root, depJobId, jobs) {
	const depMeta = jobs[depJobId];
	if (!depMeta) {
		return isJobArchived(root, depJobId);
	}
	if (depMeta.status === 'approved' || depMeta.status === 'cancelled') {
		return true;
	}
	if (depMeta.status === 'awaiting_review' && depMeta.pr) {
		try {
			const output = runGh(`pr view "${depMeta.pr}" --json state`, root);
			const parsed = JSON.parse(output);
			return parsed.state === 'MERGED';
		} catch {
			return false;
		}
	}
	return false;
}

function findDependencyBlockers(root, jobId, jobs) {
	const meta = jobs[jobId];
	if (!meta?.dependsOn?.length) {
		return [];
	}
	const blockers = [];
	for (const depJobId of meta.dependsOn) {
		if (!isDependencySatisfied(root, depJobId, jobs)) {
			const depMeta = jobs[depJobId];
			blockers.push({
				jobId: depJobId,
				status: depMeta?.status || 'missing',
				reason: 'depends-on not merged',
			});
		}
	}
	return blockers;
}

function extractPlannedFilesFromPlan(planText) {
	const files = new Set();
	const patterns = [
		new RegExp('`((?:@/)?[a-zA-Z0-9_./-]+\\.[a-zA-Z0-9]+)`', 'g'),
		/(?:^|\s)((?:app|ime|core|asr|whisper|components|lib|docs|plans|scripts|devteam)\/[a-zA-Z0-9_./-]+\.[a-zA-Z0-9]+)/gm,
	];
	for (const pattern of patterns) {
		let match;
		while ((match = pattern.exec(planText)) !== null) {
			const file = match[1].replace(/^@\//, '');
			if (file.includes('/')) {
				files.add(file);
			}
		}
	}
	return [...files];
}

function syncPlannedFiles(root, jobId) {
	const planPath = artifactPath(root, jobId, '01-plan.md');
	if (!fs.existsSync(planPath)) {
		return { jobId, plannedFiles: [] };
	}
	const planText = fs.readFileSync(planPath, 'utf8');
	const plannedFiles = extractPlannedFilesFromPlan(planText);
	writeJobMeta(root, jobId, { plannedFiles });
	return { jobId, plannedFiles };
}

function canStartJob(root, jobId, jobs) {
	if (countActiveAgentJobs(jobs) >= ACTIVE_CAP) {
		return { ok: false, reason: 'active cap reached' };
	}
	const depBlockers = findDependencyBlockers(root, jobId, jobs);
	if (depBlockers.length > 0) {
		return { ok: false, reason: 'dependency', blockers: depBlockers };
	}
	const blockers = findConflictBlockers(root, jobId, jobs);
	if (blockers.length > 0) {
		return { ok: false, reason: 'conflict', blockers };
	}
	return { ok: true };
}

function promoteQueuedJobs(root) {
	const { jobs, registry } = loadAllJobs(root);
	const promoted = [];

	for (const jobId of [...registry.queue]) {
		const meta = jobs[jobId];
		if (!meta || meta.status !== 'queued') {
			registry.queue = registry.queue.filter((id) => id !== jobId);
			continue;
		}

		syncPlannedFiles(root, jobId);
		const refreshed = loadAllJobs(root);
		const startCheck = canStartJob(root, jobId, refreshed.jobs);
		if (!startCheck.ok) {
			if (startCheck.reason === 'conflict' || startCheck.reason === 'dependency') {
				writeJobMeta(root, jobId, {
					status: 'conflict_hold',
					blockedBy: startCheck.blockers.map((b) => b.jobId),
				});
			}
			continue;
		}

		const mode = meta.mode || 'full';
		const nextStatus = mode === 'quick' ? 'coding' : 'planning';
		writeJobMeta(root, jobId, {
			status: nextStatus,
			blockedBy: [],
			stages: {
				...meta.stages,
				plan: mode === 'quick' ? 'skipped' : 'in_progress',
				code: mode === 'quick' ? 'in_progress' : meta.stages?.code || 'pending',
			},
		});
		registry.queue = registry.queue.filter((id) => id !== jobId);
		promoted.push({ jobId, status: nextStatus });
		if (countActiveAgentJobs(loadAllJobs(root).jobs) >= ACTIVE_CAP) {
			break;
		}
	}

	saveRegistry(root, registry);
	updateReadmeDashboard(root);
	return promoted;
}

function releaseConflictHolds(root) {
	const { jobs } = loadAllJobs(root);
	const released = [];
	for (const [jobId, meta] of Object.entries(jobs)) {
		if (meta.status !== 'conflict_hold') {
			continue;
		}
		const conflictBlockers = findConflictBlockers(root, jobId, jobs);
		const dependencyBlockers = findDependencyBlockers(root, jobId, jobs);
		const blockers = [...conflictBlockers, ...dependencyBlockers];
		if (blockers.length > 0) {
			writeJobMeta(root, jobId, { blockedBy: blockers.map((b) => b.jobId) });
			continue;
		}
		const registry = loadRegistry(root);
		if (!registry.queue.includes(jobId)) {
			registry.queue.push(jobId);
			saveRegistry(root, registry);
		}
		writeJobMeta(root, jobId, { status: 'queued', blockedBy: [] });
		released.push(jobId);
	}
	if (released.length > 0) {
		promoteQueuedJobs(root);
	}
	return released;
}

function submitJob(root, feature, options = {}) {
	const mode = options.mode || 'full';
	const originalAsk = options.originalAsk || feature;
	const planRef = options.planRef || null;
	const presets = loadModelPresets(root);
	const models = buildModelsFromPresets(presets);

	const registry = loadRegistry(root);
	const jobNumber = registry.nextJobNumber;
	const jobId = formatJobId(jobNumber);
	registry.nextJobNumber = jobNumber + 1;

	const dir = jobDir(root, jobId);
	fs.mkdirSync(dir, { recursive: true });

	const meta = createInitialMeta(jobId, feature, {
		mode,
		originalAsk,
		planRef,
		models,
		dependsOn: options.dependsOn || [],
	});

	if (mode === 'quick' && planRef) {
		const planSource = path.isAbsolute(planRef) ? planRef : path.join(root, planRef);
		if (fs.existsSync(planSource)) {
			fs.copyFileSync(planSource, artifactPath(root, jobId, '01-plan.md'));
			meta.plannedFiles = extractPlannedFilesFromPlan(fs.readFileSync(planSource, 'utf8'));
			meta.stages.plan = 'skipped';
		}
	}

	const { jobs } = loadAllJobs(root);
	jobs[jobId] = meta;
	const startCheck = canStartJob(root, jobId, jobs);
	if (startCheck.ok && countActiveAgentJobs(jobs) < ACTIVE_CAP) {
		// start immediately — meta.status already planning/coding
	} else {
		meta.status =
			startCheck.reason === 'conflict' || startCheck.reason === 'dependency'
				? 'conflict_hold'
				: 'queued';
		meta.blockedBy = startCheck.blockers?.map((b) => b.jobId) || [];
		if (meta.status === 'queued') {
			registry.queue.push(jobId);
		}
		meta.stages.plan = mode === 'quick' ? 'skipped' : 'pending';
		meta.stages.code = 'pending';
	}

	writeJsonFile(metaFilePath(root, jobId), meta);
	saveRegistry(root, registry);
	updateReadmeDashboard(root);

	if (meta.status === 'queued') {
		promoteQueuedJobs(root);
	}

	return { jobId, meta: readJobMeta(root, jobId), registry: loadRegistry(root) };
}

function buildDashboardMarkdown(jobs, registry) {
	const rows = Object.values(jobs).sort((a, b) => {
		const aTime = Date.parse(a.startedAt || 0);
		const bTime = Date.parse(b.startedAt || 0);
		return bTime - aTime;
	});

	const lines = [
		'| Job | Status | Feature | Branch | PR | Review |',
		'|-----|--------|---------|--------|-----|--------|',
	];

	if (rows.length === 0) {
		lines.push('| — | *No active jobs* | — | — | — | — |');
	} else {
		for (const meta of rows) {
			const reviewCell =
				meta.status === 'awaiting_review'
					? `[04-review.md](jobs/${meta.id}/04-review.md)`
					: '—';
			const prCell = meta.prNumber ? `#${meta.prNumber}` : meta.pr || '—';
			lines.push(
				`| ${meta.id} | ${meta.status} | ${meta.feature || '—'} | \`${meta.branch || '—'}\` | ${prCell} | ${reviewCell} |`,
			);
		}
	}

	const activeCount = countActiveAgentJobs(jobs);
	const queuedCount = registry.queue.length;
	const awaitingCount = Object.values(jobs).filter((m) => m.status === 'awaiting_review').length;

	const header = [
		'**Active agent jobs:** ' + `${activeCount}/${ACTIVE_CAP}`,
		'**Queued:** ' + String(queuedCount),
		'**Awaiting your review:** ' + String(awaitingCount),
		'',
	].join('\n');

	return `${header}${lines.join('\n')}`;
}

function buildReadyForReviewSection(jobs) {
	const ready = Object.values(jobs)
		.filter((meta) => meta.status === 'awaiting_review')
		.sort((a, b) => Date.parse(a.completedAt || 0) - Date.parse(b.completedAt || 0));

	if (ready.length === 0) {
		return '_No jobs awaiting review._';
	}

	return ready
		.map((meta) => `- **${meta.id}** — ${meta.feature} · PR ${meta.prNumber || meta.pr || '—'}`)
		.join('\n');
}

function updateReadmeDashboard(root) {
	const { readmePath } = getDevteamPaths(root);
	const { jobs, registry } = loadAllJobs(root);
	const table = buildDashboardMarkdown(jobs, registry);
	const ready = buildReadyForReviewSection(jobs);

	const content = `# Devteam

Job-based pipeline: up to **${ACTIVE_CAP}** active agent jobs, rest queued. Human verbs: **approve**, **revise**, **cancel**.

## Dashboard

${table}

## Ready for review

${ready}

## Commands

| Command | Action |
|---------|--------|
| \`/devteam <task>\` | Submit full job (plan → code → test → review → double_check) |
| \`/devteamquick <plan path>\` | Submit quick job (skip plan) |
| \`/devteam status\` | Show dashboard |
| \`/devteam show <job-id>\` | Walkthrough artifacts |
| \`/devteam approve <job-id>\` | Merge when CI green |
| \`/devteam revise <job-id> <notes>\` | Send back for revision |
| \`/devteam cancel <job-id>\` | Abandon job |
| \`npm run devteam:sync -- <job-id>\` | Merge \`${DEFAULT_BRANCH}\` into an open PR branch (fixes README conflicts) |

## Stacked PRs

When plan B depends on plan A:

1. Submit B with \`--depends-on job-XXX\` so B stays on \`conflict_hold\` until A merges.
2. Branch each job from \`origin/${DEFAULT_BRANCH}\` at code time — never stack feature branches against \`${DEFAULT_BRANCH}\`.
3. After approving A, run \`npm run devteam:sync -- <job-id-B>\` (or \`--all-open\`) before merging B.

\`devteam/README.md\` is auto-generated on every stage — expect trivial conflicts if branches are stacked without sync.

_Auto-updated by devteam scripts._
`;

	fs.mkdirSync(path.dirname(readmePath), { recursive: true });
	fs.writeFileSync(readmePath, content, 'utf8');
}

function runGh(args, root, options = {}) {
	const runner =
		options.runCommand ||
		((command, cwd) =>
			execSync(command, {
				cwd,
				encoding: 'utf8',
				stdio: ['ignore', 'pipe', 'pipe'],
			}));
	return runner(`gh ${args}`, root);
}

function parsePrNumber(prUrl) {
	const match = String(prUrl || '').match(/\/pull\/(\d+)/);
	return match ? Number(match[1]) : null;
}

function buildDefaultPrTitle(meta) {
	const feature = String(meta.feature || 'devteam feature').slice(0, 60);
	return `[devteam ${meta.id}] ${feature}`;
}

function buildPrBody(root, jobId, meta) {
	const template = readTemplate(root, 'pr-body.md.template');
	const planPath = `devteam/jobs/${jobId}/01-plan.md`;

	if (template) {
		return applyTemplate(template, {
			JOB_ID: jobId,
			SLOT: jobId,
			FEATURE: meta.feature || '',
			BRANCH: meta.branch || '',
			PLAN_PATH: planPath,
		});
	}

	return [
		`## Devteam ${jobId}`,
		'',
		`**Feature:** ${meta.feature}`,
		`**Original ask:** ${meta.originalAsk}`,
		`**Branch:** \`${meta.branch}\``,
		'',
		`Plan: \`${planPath}\``,
	].join('\n');
}

const SYNCABLE_JOB_STATUSES = new Set([
	'coding',
	'testing',
	'reviewing',
	'revising',
	'awaiting_review',
]);

function runGit(command, root, options = {}) {
	const runner =
		options.runCommand ||
		((cmd, cwd) =>
			execSync(cmd, {
				cwd,
				encoding: 'utf8',
				stdio: ['ignore', 'pipe', 'pipe'],
			}));
	return runner(command, root).trim();
}

function listMergeConflicts(root, options = {}) {
	const output = runGit('git diff --name-only --diff-filter=U', root, options);
	return output ? output.split('\n').filter(Boolean) : [];
}

function resolveReadmeMergeConflict(root, jobId, options = {}) {
	updateReadmeDashboard(root);
	runGit('git add devteam/README.md', root, options);
	const message = `chore(devteam): sync ${jobId} with ${DEFAULT_BRANCH} (regenerate README)`;
	runGit(`git commit -m "${message}"`, root, options);
}

function syncJobBranchWithMaster(root, jobId, options = {}) {
	const meta = readJobMeta(root, jobId);
	if (!meta) {
		throw new Error(`Job ${jobId} not found`);
	}
	if (!meta.branch) {
		throw new Error(`Job ${jobId} has no branch`);
	}
	if (!SYNCABLE_JOB_STATUSES.has(meta.status)) {
		throw new Error(`Job ${jobId} is ${meta.status}; sync only applies to in-flight jobs with a branch`);
	}

	const originalBranch = runGit('git rev-parse --abbrev-ref HEAD', root, options);
	let switched = false;
	const result = { jobId, branch: meta.branch, synced: false, readmeRegenerated: false };

	try {
		runGit(`git fetch origin ${DEFAULT_BRANCH}`, root, options);
		if (originalBranch !== meta.branch) {
			runGit(`git checkout "${meta.branch}"`, root, options);
			switched = true;
		}

		try {
			runGit(`git merge origin/${DEFAULT_BRANCH} -m "chore(devteam): sync with ${DEFAULT_BRANCH}"`, root, options);
		} catch {
			const conflicts = listMergeConflicts(root, options);
			if (conflicts.length === 1 && conflicts[0] === 'devteam/README.md') {
				resolveReadmeMergeConflict(root, jobId, options);
				result.readmeRegenerated = true;
			} else {
				throw new Error(
					conflicts.length > 0
						? `Merge conflicts remain: ${conflicts.join(', ')}`
						: 'git merge failed',
				);
			}
		}

		if (options.push !== false) {
			runGit(`git push -u origin "${meta.branch}"`, root, options);
		}

		result.synced = true;
		return result;
	} finally {
		if (switched && originalBranch && originalBranch !== 'HEAD') {
			try {
				runGit(`git checkout "${originalBranch}"`, root, options);
			} catch {
				// best-effort restore
			}
		}
	}
}

function syncDependentJobBranches(root, mergedJobId, options = {}) {
	const { jobs } = loadAllJobs(root);
	const results = [];
	for (const [jobId, meta] of Object.entries(jobs)) {
		if (!meta.dependsOn?.includes(mergedJobId) || !meta.branch) {
			continue;
		}
		if (!SYNCABLE_JOB_STATUSES.has(meta.status)) {
			continue;
		}
		try {
			results.push(syncJobBranchWithMaster(root, jobId, options));
		} catch (error) {
			results.push({ jobId, branch: meta.branch, synced: false, error: error.message });
		}
	}
	return results;
}

function syncAllOpenJobBranches(root, options = {}) {
	const { jobs } = loadAllJobs(root);
	const results = [];
	for (const [jobId, meta] of Object.entries(jobs)) {
		if (!meta.branch || !SYNCABLE_JOB_STATUSES.has(meta.status)) {
			continue;
		}
		try {
			results.push(syncJobBranchWithMaster(root, jobId, options));
		} catch (error) {
			results.push({ jobId, branch: meta.branch, synced: false, error: error.message });
		}
	}
	return results;
}

function pushBranchIfNeeded(root, branch, options = {}) {
	const runner =
		options.runCommand ||
		((command, cwd) => {
			execSync(command, { cwd, stdio: ['ignore', 'pipe', 'pipe'] });
		});
	try {
		runner(`git push -u origin "${branch}"`, root);
		return { pushed: true };
	} catch (error) {
		return { pushed: false, error: error.message };
	}
}

function openPrForJob(root, jobId, options = {}) {
	const meta = readJobMeta(root, jobId);
	if (!meta) {
		throw new Error(`Job ${jobId} not found`);
	}
	if (!meta.branch) {
		throw new Error('meta.branch is required to open a PR');
	}

	const { jobs } = loadAllJobs(root);
	const depBlockers = findDependencyBlockers(root, jobId, jobs);
	if (depBlockers.length > 0) {
		throw new Error(
			`depends-on not merged: ${depBlockers.map((blocker) => blocker.jobId).join(', ')}`,
		);
	}

	let prUrl = meta.pr || null;
	let created = false;

	try {
		const existing = runGh(`pr view "${meta.branch}" --json url,state,isDraft,number`, root, options);
		const parsed = JSON.parse(existing);
		if (parsed?.url) {
			prUrl = parsed.url;
		}
	} catch {
		// no PR yet
	}

	if (!prUrl) {
		if (options.push !== false) {
			const pushResult = pushBranchIfNeeded(root, meta.branch, options);
			if (!pushResult.pushed && !options.allowUnpushed) {
				throw new Error(`git push failed: ${pushResult.error || 'unknown error'}`);
			}
		}

		const title = options.title || buildDefaultPrTitle(meta);
		const body = options.body || buildPrBody(root, jobId, meta);
		const bodyFile = path.join(jobDir(root, jobId), '.pr-body.tmp.md');
		fs.writeFileSync(bodyFile, body, 'utf8');

		try {
			const createOutput = runGh(
				`pr create --draft --head "${meta.branch}" --base ${DEFAULT_BRANCH} --title "${title.replace(/"/g, '\\"')}" --body-file "${bodyFile.replace(/\\/g, '/')}"`,
				root,
				options,
			);
			const urlMatch = String(createOutput).match(/https:\/\/github\.com\/\S+/);
			prUrl = urlMatch ? urlMatch[0] : null;
			created = Boolean(prUrl);
		} finally {
			if (fs.existsSync(bodyFile)) {
				fs.unlinkSync(bodyFile);
			}
		}

		if (!prUrl) {
			const viewOutput = runGh(`pr view "${meta.branch}" --json url,number`, root, options);
			const parsed = JSON.parse(viewOutput);
			prUrl = parsed.url;
			created = Boolean(prUrl);
		}
	}

	if (!prUrl) {
		throw new Error('Failed to resolve PR URL after create');
	}

	const prNumber = parsePrNumber(prUrl);
	writeJobMeta(root, jobId, {
		pr: prUrl,
		prNumber,
		chatTitle: prNumber ? `devteam pr${prNumber}` : meta.chatTitle,
	});

	return { jobId, pr: prUrl, prNumber, created, chatTitle: prNumber ? `devteam pr${prNumber}` : null };
}

function checkPrForJob(root, jobId, options = {}) {
	const meta = readJobMeta(root, jobId);
	if (!meta?.branch) {
		return { jobId, valid: false, reason: 'missing branch' };
	}
	if (options.mockGhPr) {
		const valid = !meta.pr || meta.pr === options.mockGhPr.url;
		return {
			jobId,
			valid,
			reason: valid ? null : 'meta.pr mismatch',
			metaPr: meta.pr || null,
			ghPr: options.mockGhPr,
		};
	}
	try {
		const output = runGh(`pr view "${meta.branch}" --json url,state,isDraft,number`, root, options);
		const ghPr = JSON.parse(output);
		const valid = Boolean(ghPr?.url) && (!meta.pr || meta.pr === ghPr.url);
		return { jobId, valid, reason: valid ? null : 'meta.pr mismatch', metaPr: meta.pr, ghPr };
	} catch {
		return { jobId, valid: false, reason: 'no open PR found', metaPr: meta.pr, ghPr: null };
	}
}

function validateJob(root, jobId, options = {}) {
	const stage = options.stage || null;
	const errors = [];
	const meta = readJobMeta(root, jobId);
	if (!meta) {
		return { jobId, valid: false, errors: ['job not found'] };
	}

	const stageChecks = {
		plan: ['01-plan.md'],
		code: ['02-code-summary.md'],
		test: ['03-test-report.md'],
		review: ['04-review.md'],
		double_checking: ['05-double-check.md'],
	};

	function shouldCheck(name) {
		return !stage || stage === name;
	}

	for (const [name, files] of Object.entries(stageChecks)) {
		if (!shouldCheck(name)) {
			continue;
		}
		for (const file of files) {
			if (!fs.existsSync(artifactPath(root, jobId, file)) && meta.stages?.[name] === 'done') {
				errors.push(`missing ${file} for stage ${name} marked done`);
			}
		}
	}

	if (shouldCheck('code') && meta.stages?.code === 'done' && !meta.pr) {
		errors.push('pr URL required after code stage');
	}

	if (meta.status === 'awaiting_review') {
		if (!fs.existsSync(artifactPath(root, jobId, '04-review.md'))) {
			errors.push('awaiting_review but 04-review.md missing');
		}
		if (!fs.existsSync(artifactPath(root, jobId, '05-double-check.md'))) {
			errors.push('awaiting_review but 05-double-check.md missing');
		}
	}

	return { jobId, valid: errors.length === 0, errors, meta };
}

function notifyReviewReady(root, jobId, options = {}) {
	const meta = readJobMeta(root, jobId);
	if (!meta?.pr) {
		return { jobId, notified: false, reason: 'no pr URL' };
	}
	if (meta.notifiedAt && !options.force) {
		return { jobId, notified: false, reason: 'already notified', notifiedAt: meta.notifiedAt };
	}

	const body = [
		`✅ **Devteam ${jobId} ready for review**`,
		'',
		`Start with \`devteam/jobs/${jobId}/04-review.md\` on this branch.`,
		'',
		`Feature: ${meta.feature}`,
		`Chat title: \`${meta.chatTitle || 'devteam pr???'}\``,
		'',
		'Reply: `/devteam approve`, `/devteam revise`, or `/devteam cancel`',
	].join('\n');

	runGh(`pr comment "${meta.pr}" --body "${body.replace(/"/g, '\\"')}"`, root, options);
	writeJobMeta(root, jobId, { notifiedAt: new Date().toISOString() });
	return { jobId, notified: true, pr: meta.pr };
}

function recordTestFailure(root, jobId) {
	const meta = readJobMeta(root, jobId);
	const testRetries = (meta.retryCounts?.test || 0) + 1;
	if (testRetries >= MAX_TEST_RETRIES) {
		writeJobMeta(root, jobId, {
			status: 'blocked',
			retryCounts: { ...meta.retryCounts, test: testRetries },
		});
		return { jobId, action: 'blocked', testRetries };
	}
	writeJobMeta(root, jobId, {
		status: 'coding',
		retryCounts: { ...meta.retryCounts, test: testRetries },
		stages: { ...meta.stages, test: 'pending', code: 'in_progress' },
	});
	return { jobId, action: 'retry_coding', testRetries };
}

function recordDoubleCheckFailure(root, jobId) {
	const meta = readJobMeta(root, jobId);
	const retries = (meta.retryCounts?.doubleCheck || 0) + 1;
	if (retries >= MAX_DOUBLE_CHECK_RETRIES) {
		writeJobMeta(root, jobId, {
			status: 'blocked',
			retryCounts: { ...meta.retryCounts, doubleCheck: retries },
		});
		return { jobId, action: 'blocked', doubleCheckRetries: retries };
	}
	writeJobMeta(root, jobId, {
		status: 'reviewing',
		retryCounts: { ...meta.retryCounts, doubleCheck: retries },
		stages: { ...meta.stages, doubleCheck: 'pending', review: 'in_progress' },
	});
	return { jobId, action: 'retry_review', doubleCheckRetries: retries };
}

function getAdvanceChecks(root, jobId, targetStage) {
	if (!ADVANCE_TARGETS.has(targetStage)) {
		throw new Error(`Invalid advance target "${targetStage}"`);
	}

	const checks = [];
	const blockers = [];
	const meta = readJobMeta(root, jobId);
	if (!meta) {
		blockers.push(`job ${jobId} not found`);
		return { jobId, targetStage, checks, blockers };
	}

	if (targetStage === 'coding') {
		checks.push('01-plan.md exists (or quick mode skipped)');
		if (meta.mode !== 'quick' && !fs.existsSync(artifactPath(root, jobId, '01-plan.md'))) {
			blockers.push('missing 01-plan.md');
		}
		checks.push('sync-planned-files');
		checks.push('conflicts check');
	} else if (targetStage === 'testing') {
		checks.push('02-code-summary.md exists');
		if (!fs.existsSync(artifactPath(root, jobId, '02-code-summary.md'))) {
			blockers.push('missing 02-code-summary.md');
		}
		checks.push('open-pr if needed');
	} else if (targetStage === 'reviewing') {
		checks.push('03-test-report.md exists');
		if (!fs.existsSync(artifactPath(root, jobId, '03-test-report.md'))) {
			blockers.push('missing 03-test-report.md');
		}
	} else if (targetStage === 'double_checking') {
		checks.push('04-review.md exists');
		if (!fs.existsSync(artifactPath(root, jobId, '04-review.md'))) {
			blockers.push('missing 04-review.md');
		}
	} else if (targetStage === 'awaiting_review') {
		checks.push('05-double-check.md exists');
		if (!fs.existsSync(artifactPath(root, jobId, '05-double-check.md'))) {
			blockers.push('missing 05-double-check.md');
		}
		checks.push('full validate');
	}

	return { jobId, targetStage, checks, blockers, meta };
}

function advanceJob(root, jobId, targetStage, options = {}) {
	const report = getAdvanceChecks(root, jobId, targetStage);
	if (report.blockers.length > 0) {
		throw new Error(report.blockers.join('; '));
	}

	const now = new Date().toISOString();
	const meta = report.meta;

	if (targetStage === 'coding') {
		syncPlannedFiles(root, jobId);
		const { jobs } = loadAllJobs(root);
		const conflictBlockers = findConflictBlockers(root, jobId, jobs);
		const dependencyBlockers = findDependencyBlockers(root, jobId, jobs);
		const blockers = [...conflictBlockers, ...dependencyBlockers];
		if (blockers.length > 0) {
			writeJobMeta(root, jobId, {
				status: 'conflict_hold',
				blockedBy: blockers.map((b) => b.jobId),
			});
			throw new Error(`conflict hold: blocked by ${blockers.map((b) => b.jobId).join(', ')}`);
		}
		const validation = validateJob(root, jobId, { stage: 'plan' });
		if (meta.mode !== 'quick' && !validation.valid) {
			throw new Error(validation.errors.join('; '));
		}
		writeJobMeta(root, jobId, {
			stages: { ...meta.stages, plan: meta.mode === 'quick' ? 'skipped' : 'done', code: 'in_progress' },
			status: 'coding',
			blockedBy: [],
		});
	} else if (targetStage === 'testing') {
		if (options.ensurePr !== false && !meta.pr) {
			openPrForJob(root, jobId, options);
		}
		const prCheck = checkPrForJob(root, jobId, options);
		if (!prCheck.valid) {
			throw new Error(prCheck.reason || 'pr-check failed');
		}
		writeJobMeta(root, jobId, {
			stages: { ...meta.stages, code: 'done' },
			status: 'testing',
			pr: prCheck.ghPr?.url || meta.pr,
			prNumber: prCheck.ghPr?.number || meta.prNumber,
			chatTitle:
				prCheck.ghPr?.number != null ? `devteam pr${prCheck.ghPr.number}` : meta.chatTitle,
		});
	} else if (targetStage === 'reviewing') {
		writeJobMeta(root, jobId, {
			stages: { ...meta.stages, test: 'done', review: 'in_progress' },
			status: 'reviewing',
		});
	} else if (targetStage === 'double_checking') {
		writeJobMeta(root, jobId, {
			stages: { ...meta.stages, review: 'done', doubleCheck: 'in_progress' },
			status: 'double_checking',
		});
	} else if (targetStage === 'awaiting_review') {
		const validation = validateJob(root, jobId);
		if (!validation.valid) {
			throw new Error(validation.errors.join('; '));
		}
		writeJobMeta(root, jobId, {
			stages: { ...meta.stages, review: 'done', doubleCheck: 'done' },
			status: 'awaiting_review',
			completedAt: now,
		});
		releaseConflictHolds(root);
		promoteQueuedJobs(root);
		if (!options.skipNotify) {
			try {
				notifyReviewReady(root, jobId, options);
			} catch (error) {
				return { jobId, targetStage, advanced: true, notifyWarning: error.message };
			}
		}
	}

	return { jobId, targetStage, advanced: true, meta: readJobMeta(root, jobId) };
}

function reviseJob(root, jobId, notes) {
	const meta = readJobMeta(root, jobId);
	if (!meta) {
		throw new Error(`Job ${jobId} not found`);
	}
	if (meta.status !== 'awaiting_review' && meta.status !== 'blocked') {
		throw new Error(`Job ${jobId} is ${meta.status}; can only revise awaiting_review or blocked jobs`);
	}

	const revisionNotes = [...(meta.revisionNotes || []), { at: new Date().toISOString(), notes }];
	writeJobMeta(root, jobId, {
		status: 'revising',
		revisionNotes,
		retryCounts: { test: 0 },
		completedAt: null,
		notifiedAt: null,
		stages: {
			...meta.stages,
			code: 'in_progress',
			test: 'pending',
			review: 'pending',
			doubleCheck: 'pending',
		},
	});
	return { jobId, status: 'revising', revisionNotes };
}

function cancelJob(root, jobId, options = {}) {
	const meta = readJobMeta(root, jobId);
	if (!meta) {
		throw new Error(`Job ${jobId} not found`);
	}

	if (meta.pr && !options.skipGh) {
		try {
			runGh(`pr close "${meta.pr}"`, root, options);
		} catch {
			// PR may already be closed
		}
	}

	writeJobMeta(root, jobId, { status: 'cancelled', completedAt: new Date().toISOString() });
	const archived = archiveJob(root, jobId, { reason: 'cancelled', force: true });
	releaseConflictHolds(root);
	promoteQueuedJobs(root);
	return { jobId, archived };
}

function waitForCiGreen(root, jobId, options = {}) {
	const meta = readJobMeta(root, jobId);
	if (!meta?.pr) {
		throw new Error('PR required before approve');
	}

	const maxAttempts = options.maxAttempts || 60;
	const delayMs = options.delayMs || 10000;

	for (let attempt = 0; attempt < maxAttempts; attempt++) {
		const output = runGh(
			`pr view "${meta.pr}" --json mergeable,mergeStateStatus,statusCheckRollup,state`,
			root,
			options,
		);
		const pr = JSON.parse(output);
		const checks = pr.statusCheckRollup?.state;
		const mergeable = pr.mergeable === 'MERGEABLE';
		const ciGreen = checks === 'SUCCESS' || checks === 'NEUTRAL' || !checks;

		if (pr.state === 'MERGED') {
			return { jobId, merged: true, alreadyMerged: true };
		}

		if (mergeable && ciGreen) {
			return { jobId, ready: true, pr };
		}

		if (options.runCommand) {
			break;
		}

		const end = Date.now() + delayMs;
		while (Date.now() < end) {
			// sync wait between CI polls
		}
	}

	return { jobId, ready: false, reason: 'CI not green or PR not mergeable yet' };
}

function approveJob(root, jobId, options = {}) {
	const meta = readJobMeta(root, jobId);
	if (!meta) {
		throw new Error(`Job ${jobId} not found`);
	}
	if (meta.status !== 'awaiting_review') {
		throw new Error(`Job ${jobId} is ${meta.status}; can only approve awaiting_review jobs`);
	}

	writeJobMeta(root, jobId, { status: 'approved_pending_ci' });

	const ci = waitForCiGreen(root, jobId, options);
	if (!ci.ready && !ci.alreadyMerged) {
		writeJobMeta(root, jobId, { status: 'awaiting_review' });
		throw new Error(ci.reason || 'PR not ready to merge');
	}

	if (!ci.alreadyMerged) {
		runGh(`pr merge "${meta.pr}" --merge --delete-branch`, root, options);
	}

	writeJobMeta(root, jobId, { status: 'approved', completedAt: new Date().toISOString() });
	const archived = archiveJob(root, jobId, { reason: 'approved', force: true });
	releaseConflictHolds(root);
	promoteQueuedJobs(root);
	const syncedDependents = syncDependentJobBranches(root, jobId, options);
	return { jobId, merged: true, archived, syncedDependents };
}

function archiveJob(root, jobId, options = {}) {
	const { force = false, reason = 'approved' } = options;
	const dir = jobDir(root, jobId);
	const meta = readJobMeta(root, jobId);
	if (!meta) {
		throw new Error(`Job ${jobId} not found`);
	}

	const archivable = new Set(['awaiting_review', 'approved', 'cancelled', 'blocked']);
	if (!archivable.has(meta.status) && !force) {
		throw new Error(`Refusing to archive ${jobId} with status "${meta.status}"`);
	}

	const slug = meta.slug || slugify(meta.feature);
	const date = new Date().toISOString().slice(0, 10);
	const { archiveDir } = getDevteamPaths(root);
	fs.mkdirSync(archiveDir, { recursive: true });

	const archiveName = `${jobId}-${slug}-${date}`;
	const dest = path.join(archiveDir, archiveName);
	if (fs.existsSync(dest)) {
		throw new Error(`Archive destination already exists: ${dest}`);
	}

	fs.renameSync(dir, dest);
	const archivedMeta = {
		...meta,
		status: meta.status === 'cancelled' ? 'cancelled' : 'archived',
		archivedAt: new Date().toISOString(),
		archiveReason: reason,
	};
	writeJsonFile(path.join(dest, 'meta.json'), archivedMeta);

	const registry = loadRegistry(root);
	registry.queue = registry.queue.filter((id) => id !== jobId);
	saveRegistry(root, registry);
	updateReadmeDashboard(root);

	return { jobId, archivePath: dest };
}

function readArtifactExcerpt(root, jobId, filename, maxLines = 12) {
	const filePath = artifactPath(root, jobId, filename);
	if (!fs.existsSync(filePath)) {
		return { exists: false, excerpt: '', lineCount: 0 };
	}
	const lines = fs.readFileSync(filePath, 'utf8').split('\n');
	return {
		exists: true,
		excerpt: lines.slice(0, maxLines).join('\n'),
		lineCount: lines.length,
	};
}

function buildShowReport(root, jobId) {
	const meta = readJobMeta(root, jobId);
	if (!meta) {
		throw new Error(`Job ${jobId} not found`);
	}

	const artifacts = {};
	for (const item of ARTIFACTS) {
		artifacts[item.key] = readArtifactExcerpt(root, jobId, item.file);
	}

	return {
		jobId,
		summary: meta,
		artifacts,
	};
}

function formatShowReport(report) {
	const meta = report.summary;
	const lines = [
		`# Devteam ${report.jobId} walkthrough`,
		'',
		`**Feature:** ${meta.feature}`,
		`**Original ask:** ${meta.originalAsk}`,
		`**Status:** ${meta.status}`,
		`**Branch:** ${meta.branch || 'unknown'}`,
		`**PR:** ${meta.pr || '—'}`,
		`**Chat title:** ${meta.chatTitle || '—'}`,
		'',
	];

	for (const item of ARTIFACTS) {
		const artifact = report.artifacts[item.key];
		lines.push(`## ${item.file}`);
		if (!artifact.exists) {
			lines.push('_Not created yet._');
		} else {
			lines.push('```markdown');
			lines.push(artifact.excerpt);
			if (artifact.lineCount > 12) {
				lines.push('...');
			}
			lines.push('```');
		}
		lines.push('');
	}

	return lines.join('\n');
}

function buildStatusReport(root) {
	releaseConflictHolds(root);
	promoteQueuedJobs(root);
	const refreshed = loadAllJobs(root);
	updateReadmeDashboard(root);
	return {
		jobs: refreshed.jobs,
		registry: refreshed.registry,
		activeCount: countActiveAgentJobs(refreshed.jobs),
		dashboard: buildDashboardMarkdown(refreshed.jobs, refreshed.registry),
		readyForReview: buildReadyForReviewSection(refreshed.jobs),
	};
}

function autoArchiveMergedJobs(root, options = {}) {
	const archived = [];
	const { jobsDir } = getDevteamPaths(root);
	if (!fs.existsSync(jobsDir)) {
		return archived;
	}

	for (const jobId of listJobIds(root)) {
		const meta = readJobMeta(root, jobId);
		if (!meta?.branch || TERMINAL_STATUSES.has(meta.status)) {
			continue;
		}
		try {
			const output = runGh(`pr view "${meta.branch}" --json state,mergedAt`, root, options);
			const pr = JSON.parse(output);
			if (pr.state === 'MERGED') {
				writeJobMeta(root, jobId, { status: 'approved', completedAt: pr.mergedAt || new Date().toISOString() });
				archived.push(archiveJob(root, jobId, { reason: 'merged_on_github', force: true }));
			}
		} catch {
			// branch may not have PR
		}
	}

	if (archived.length > 0) {
		releaseConflictHolds(root);
		promoteQueuedJobs(root);
	}
	return archived;
}

module.exports = {
	ACTIVE_CAP,
	MAX_TEST_RETRIES,
	VALID_STATUSES,
	ACTIVE_AGENT_STATUSES,
	ARTIFACTS,
	slugify,
	getDevteamPaths,
	loadAllJobs,
	loadRegistry,
	submitJob,
	promoteQueuedJobs,
	releaseConflictHolds,
	analyzeConflicts,
	findConflictBlockers,
	findDependencyBlockers,
	isDependencySatisfied,
	syncPlannedFiles,
	extractPlannedFilesFromPlan,
	openPrForJob,
	checkPrForJob,
	validateJob,
	advanceJob,
	getAdvanceChecks,
	recordTestFailure,
	recordDoubleCheckFailure,
	reviseJob,
	cancelJob,
	approveJob,
	archiveJob,
	notifyReviewReady,
	buildShowReport,
	formatShowReport,
	buildStatusReport,
	buildDashboardMarkdown,
	buildReadyForReviewSection,
	updateReadmeDashboard,
	autoArchiveMergedJobs,
	syncJobBranchWithMaster,
	syncDependentJobBranches,
	syncAllOpenJobBranches,
	parseJobIdFromBranch,
	formatJobId,
	readJobMeta,
	writeJobMeta,
	countActiveAgentJobs,
	loadModelPresets,
	buildModelsFromPresets,
	getJobModels,
	validateDevteamAgentModels,
	formatDevteamModelsReport,
	DEVTEAM_MODEL_PRESET_KEYS,
	DEVTEAM_STAGE_AGENT_PATHS,
	DEVTEAM_STAGE_SUBAGENT_TYPES,
};
