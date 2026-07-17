#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const scannerRoot = path.resolve(scriptDirectory, '..');
const allinwebRoot = path.dirname(scannerRoot);
const reactRoot = process.env.AR_REACT_ROOT || path.join(allinwebRoot, 'abr-react-ts-grid');
const engineRoot = process.env.AR_ENGINE_ROOT || path.join(allinwebRoot, 'ar-web-engine');
const outputPath = path.join(scannerRoot, 'src', 'main', 'resources', 'automation-tests.json');

const projectOrder = new Map([
  ['AR Web Scanner', 0],
  ['AR React UI', 1],
  ['AR Web Engine', 2],
]);

function walkFiles(root, predicate = () => true) {
  if (!fs.existsSync(root)) return [];
  const result = [];
  const visit = directory => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      if ([
        '.git',
        'node_modules',
        'target',
        'build',
        'coverage',
        'playwright-report',
        'test-results',
      ].includes(entry.name)) {
        continue;
      }
      const fullPath = path.join(directory, entry.name);
      if (entry.isDirectory()) visit(fullPath);
      else if (entry.isFile() && predicate(fullPath)) result.push(fullPath);
    }
  };
  visit(root);
  return result.sort((left, right) => left.localeCompare(right));
}

function relative(root, file) {
  return path.relative(root, file).split(path.sep).join('/');
}

function gitValue(root, args, fallback = '') {
  try {
    return execFileSync('git', ['-C', root, ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
  } catch (_) {
    return fallback;
  }
}

function repoMetadata(project, root) {
  return {
    project,
    repository: path.basename(root),
    branch: gitValue(root, ['branch', '--show-current'], 'unknown'),
    commit: gitValue(root, ['rev-parse', 'HEAD'], 'unknown'),
  };
}

function javaMethodSignature(lines, annotationIndex) {
  const signatureLines = [];
  for (let index = annotationIndex + 1; index < Math.min(lines.length, annotationIndex + 35); index++) {
    const trimmed = lines[index].trim();
    if (!trimmed || trimmed.startsWith('//') || trimmed.startsWith('*')) continue;
    if (trimmed.startsWith('@')) continue;
    signatureLines.push(trimmed);
    const joined = signatureLines.join(' ');
    const match = joined.match(/\b(?:void|[A-Za-z_$][\w$.[\]<>?,]*)\s+([A-Za-z_$][\w$]*)\s*\(/);
    if (match) return { methodName: match[1], methodLine: index + 1 };
    if (trimmed.includes('{') || trimmed.endsWith(';')) break;
  }
  return { methodName: `unresolvedTestAtLine${annotationIndex + 1}`, methodLine: annotationIndex + 1 };
}

function nearbyAnnotation(lines, start, end, annotation) {
  const pattern = new RegExp(`@${annotation}\\s*\\(\\s*"((?:\\\\.|[^"\\\\])*)"`);
  for (let index = Math.max(0, start); index <= Math.min(lines.length - 1, end); index++) {
    const match = lines[index].match(pattern);
    if (match) return match[1].replace(/\\"/g, '"');
  }
  return '';
}

function javaRuntime(source, integration) {
  const createsBrowser = /Playwright\.create|new\s+ARPlaywrightDriver\s*\(|playwright\.chromium\s*\(/.test(source);
  if (integration && createsBrowser) return 'LIVE_BROWSER';
  if (/setHeadless\s*\(\s*true\s*\)/.test(source)) return 'HEADLESS_BROWSER';
  if (/new\s+ARPlaywrightDriver\s*\(/.test(source)) return 'HEADED_BROWSER';
  return 'JVM';
}

function javaKind(source, integration) {
  if (integration) return 'INTEGRATION';
  if (/Playwright\.create|new\s+ARPlaywrightDriver\s*\(/.test(source)) return 'PLAYWRIGHT';
  return 'UNIT';
}

function javaExecution(source, integration, disabled) {
  if (disabled) return 'DISABLED';
  if (integration || /@EnabledIfSystemProperty\b/.test(source)) return 'OPT_IN';
  if (/\bassume(?:True|False|That)\s*\(/.test(source) || /assumeBrowserLaunchAvailable\s*\(/.test(source)) {
    return 'CONDITIONAL';
  }
  return 'DEFAULT';
}

function safetyFor(runtime, execution) {
  if (execution === 'DISABLED') return 'DISABLED';
  if (runtime === 'LIVE_BROWSER') return 'LIVE_EXTERNAL';
  if (runtime === 'HEADED_BROWSER') return 'HEADED';
  if (execution === 'CONDITIONAL') return 'LOCAL_REQUIREMENT';
  return 'SAFE';
}

function discoverJavaProject(project, root) {
  const testRoot = path.join(root, 'src', 'test', 'java');
  const javaFiles = walkFiles(testRoot, file => file.endsWith('.java'));
  const tests = [];
  const artifacts = [];
  const annotationPattern = /^\s*@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\b/;

  for (const file of javaFiles) {
    const source = fs.readFileSync(file, 'utf8');
    const lines = source.split(/\r?\n/);
    const sourcePath = relative(root, file);
    const packageName = source.match(/\bpackage\s+([\w.]+)\s*;/)?.[1] || '';
    const className = path.basename(file, '.java');
    const suite = packageName ? `${packageName}.${className}` : className;
    const integration = className.endsWith('IT');
    const classDisabled = /@Disabled(?:\s*\([^)]*\))?\s*(?:public\s+)?(?:final\s+)?class\b/s.test(source);
    let declarationCount = 0;

    for (let index = 0; index < lines.length; index++) {
      const annotation = lines[index].match(annotationPattern);
      if (!annotation) continue;
      declarationCount++;
      const signature = javaMethodSignature(lines, index);
      const displayName = nearbyAnnotation(lines, index - 8, signature.methodLine - 1, 'DisplayName');
      const tag = nearbyAnnotation(lines, index - 8, signature.methodLine - 1, 'Tag');
      const methodDisabled = lines.slice(Math.max(0, index - 8), signature.methodLine)
        .some(line => /@Disabled\b/.test(line));
      const disabled = classDisabled || methodDisabled;
      const runtime = javaRuntime(source, integration);
      const execution = javaExecution(source, integration, disabled);
      const kind = javaKind(source, integration);
      tests.push({
        id: `junit:${suite}#${signature.methodName}@${signature.methodLine}`,
        project,
        repository: path.basename(root),
        recordType: 'AUTOMATED_CASE',
        suite,
        name: signature.methodName,
        displayName: displayName || signature.methodName,
        framework: 'JUnit 5',
        language: 'Java',
        kind,
        runtime,
        execution,
        safety: safetyFor(runtime, execution),
        sourcePath,
        line: signature.methodLine,
        caseCount: 1,
        runnable: execution !== 'DISABLED',
        runAllEligible: safetyFor(runtime, execution) === 'SAFE' && execution === 'DEFAULT',
        command: `mvn -Dtest=${suite}#${signature.methodName} test`,
        tags: [annotation[1], ...(tag ? [tag] : [])],
      });
    }

    if (declarationCount === 0 && /\bstatic\s+void\s+main\s*\(/.test(source)) {
      artifacts.push({
        id: `manual:${path.basename(root)}:${sourcePath}`,
        project,
        repository: path.basename(root),
        recordType: 'MANUAL_TOOL',
        suite,
        name: className,
        displayName: className,
        framework: 'Manual Java',
        language: 'Java',
        kind: 'MANUAL',
        runtime: /Playwright|WebDriver|Selenium/.test(source) ? 'HEADED_BROWSER' : 'JVM',
        execution: 'MANUAL_ONLY',
        safety: 'DANGEROUS',
        sourcePath,
        line: Number(source.slice(0, source.search(/\bstatic\s+void\s+main\s*\(/)).split(/\r?\n/).length),
        caseCount: 0,
        runnable: false,
        runAllEligible: false,
        command: '',
        tags: ['manual', 'main'],
      });
    } else if (declarationCount === 0) {
      artifacts.push({
        id: `support:${path.basename(root)}:${sourcePath}`,
        project,
        repository: path.basename(root),
        recordType: 'SUPPORT',
        suite,
        name: className,
        displayName: className,
        framework: 'Test support',
        language: 'Java',
        kind: 'SUPPORT',
        runtime: 'JVM',
        execution: 'NOT_EXECUTABLE',
        safety: 'SUPPORT_ONLY',
        sourcePath,
        line: 1,
        caseCount: 0,
        runnable: false,
        runAllEligible: false,
        command: '',
        tags: ['support'],
      });
    }
  }

  const mainSmoke = path.join(root, 'src', 'main', 'java', 'com', 'allinweb', 'ch', 'ocr', 'bridge', 'OcrBridgeSmokeTest.java');
  if (fs.existsSync(mainSmoke) && fs.statSync(mainSmoke).isFile()) {
    artifacts.push({
      id: 'manual:ar-web-selenium:com.allinweb.ch.ocr.bridge.OcrBridgeSmokeTest',
      project,
      repository: path.basename(root),
      recordType: 'MANUAL_TOOL',
      suite: 'com.allinweb.ch.ocr.bridge.OcrBridgeSmokeTest',
      name: 'OcrBridgeSmokeTest',
      displayName: 'OCR native bridge smoke test',
      framework: 'Manual Java',
      language: 'Java',
      kind: 'MANUAL',
      runtime: 'NATIVE_LOCAL',
      execution: 'MANUAL_ONLY',
      safety: 'LOCAL_REQUIREMENT',
      sourcePath: relative(root, mainSmoke),
      line: 1,
      caseCount: 0,
      runnable: false,
      runAllEligible: false,
      command: '',
      tags: ['manual', 'native', 'ocr'],
    });
  }

  return { javaFiles, tests, artifacts };
}

function stringValue(ts, node, sourceFile) {
  if (!node) return '';
  if (ts.isStringLiteralLike(node)) return node.text;
  return node.getText(sourceFile).replace(/^['"`]|['"`]$/g, '');
}

function testTitle(ts, node) {
  return node && ts.isStringLiteralLike(node) ? node.text : '';
}

function baseCall(ts, expression) {
  const modifiers = [];
  let eachArguments = null;
  let current = expression;
  while (current) {
    if (ts.isCallExpression(current)) {
      eachArguments ||= current.arguments;
      current = current.expression;
      continue;
    }
    if (ts.isPropertyAccessExpression(current)) {
      modifiers.unshift(current.name.text);
      current = current.expression;
      continue;
    }
    if (ts.isIdentifier(current)) return { base: current.text, modifiers, eachArguments };
    break;
  }
  return null;
}

function tableRows(ts, eachArguments) {
  if (!eachArguments?.length || !ts.isArrayLiteralExpression(eachArguments[0])) return [];
  return eachArguments[0].elements.map(element => {
    if (ts.isArrayLiteralExpression(element)) return element.elements.map(cell => stringValue(ts, cell));
    return [stringValue(ts, element)];
  });
}

function expandEachName(template, row) {
  let valueIndex = 0;
  return template.replace(/%[sidf]/g, token => {
    const value = row[valueIndex++] ?? token;
    return String(value);
  });
}

async function discoverReactProject(project, root) {
  const typescriptPath = path.join(root, 'node_modules', 'typescript', 'lib', 'typescript.js');
  if (!fs.existsSync(typescriptPath) || !fs.statSync(typescriptPath).isFile()) {
    throw new Error(`TypeScript compiler not found at ${typescriptPath}. Run npm install in ${root}.`);
  }
  const imported = await import(pathToFileURL(typescriptPath).href);
  const ts = imported.default || imported;
  // Keep the inventory repository-wide so React-owned browser suites under e2e/ or tests/
  // are catalogued alongside the Jest files under src/. Generated output and dependencies
  // are excluded by walkFiles above.
  const testFiles = walkFiles(root, file => /\.(test|spec)\.tsx?$/.test(file));
  const tests = [];

  for (const file of testFiles) {
    const sourceText = fs.readFileSync(file, 'utf8');
    const sourcePath = relative(root, file);
    const isPlaywright = /from\s+['"]@playwright\/test['"]|require\s*\(\s*['"]@playwright\/test['"]\s*\)/.test(sourceText);
    const hasMockedTransport = /class\s+MockWebSocket\b|addInitScript\s*\([^)]*WebSocket/s.test(sourceText);
    const sourceFile = ts.createSourceFile(
      sourcePath,
      sourceText,
      ts.ScriptTarget.Latest,
      true,
      file.endsWith('.tsx') ? ts.ScriptKind.TSX : ts.ScriptKind.TS,
    );

    const visit = (node, describePath = []) => {
      if (ts.isCallExpression(node)) {
        const call = baseCall(ts, node.expression);
        const title = testTitle(ts, node.arguments[0]);
        if (call?.base === 'describe' && title) {
          const callback = node.arguments[node.arguments.length - 1];
          const body = callback && (ts.isArrowFunction(callback) || ts.isFunctionExpression(callback)) ? callback.body : null;
          if (body) {
            ts.forEachChild(body, child => visit(child, [...describePath, title]));
            return;
          }
        }
        if ((call?.base === 'test' || call?.base === 'it') && title) {
          const rows = call.modifiers.includes('each') ? tableRows(ts, call.eachArguments) : [];
          const expandedRows = rows.length ? rows : [[]];
          expandedRows.forEach((row, rowIndex) => {
            const displayName = rows.length ? expandEachName(title, row) : title;
            const execution = call.modifiers.includes('skip') || call.modifiers.includes('todo') ? 'DISABLED' : 'DEFAULT';
            const line = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile)).line + 1;
            const suiteName = describePath.length ? describePath.join(' > ') : path.basename(file).replace(/\.(test|spec)\.tsx?$/, '');
            tests.push({
              id: `${isPlaywright ? 'playwright' : 'jest'}:${sourcePath}::${describePath.join(' > ')}::${displayName}@${line}${rows.length ? `:${rowIndex + 1}` : ''}`,
              project,
              repository: path.basename(root),
              recordType: 'AUTOMATED_CASE',
              suite: suiteName,
              name: displayName,
              displayName,
              framework: isPlaywright ? 'Playwright Test' : 'Jest / Testing Library',
              language: 'TypeScript',
              kind: isPlaywright ? 'PLAYWRIGHT' : 'FRONTEND',
              runtime: isPlaywright ? 'HEADLESS_BROWSER' : 'JSDOM',
              execution,
              safety: execution === 'DISABLED'
                ? 'DISABLED'
                : isPlaywright && !hasMockedTransport ? 'LOCAL_REQUIREMENT' : 'SAFE',
              sourcePath,
              line,
              caseCount: 1,
              runnable: execution !== 'DISABLED',
              runAllEligible: execution === 'DEFAULT' && (!isPlaywright || hasMockedTransport),
              command: isPlaywright
                ? `npm run test:e2e -- --grep ${JSON.stringify(displayName)}`
                : `npm test -- --runInBand --runTestsByPath ${sourcePath} -t ${JSON.stringify(displayName)}`,
              tags: ['frontend', isPlaywright ? 'playwright' : call.base, ...call.modifiers],
            });
          });
        }
      }
      ts.forEachChild(node, child => visit(child, describePath));
    };

    visit(sourceFile);
  }

  const artifacts = [];
  for (const sourcePath of ['src/components/ErrorTest.tsx', 'src/components/scanner/Scanner.testUtils.ts', 'src/setupTests.ts']) {
    const file = path.join(root, ...sourcePath.split('/'));
    if (!fs.existsSync(file) || !fs.statSync(file).isFile()) continue;
    const manual = sourcePath.endsWith('ErrorTest.tsx');
    artifacts.push({
      id: `${manual ? 'manual' : 'support'}:${path.basename(root)}:${sourcePath}`,
      project,
      repository: path.basename(root),
      recordType: manual ? 'MANUAL_TOOL' : 'SUPPORT',
      suite: sourcePath,
      name: path.basename(sourcePath),
      displayName: manual ? 'Manual WebSocket error harness' : path.basename(sourcePath),
      framework: manual ? 'Manual React harness' : 'Test support',
      language: 'TypeScript',
      kind: manual ? 'MANUAL' : 'SUPPORT',
      runtime: manual ? 'BROWSER' : 'JSDOM',
      execution: manual ? 'MANUAL_ONLY' : 'NOT_EXECUTABLE',
      safety: manual ? 'LOCAL_REQUIREMENT' : 'SUPPORT_ONLY',
      sourcePath,
      line: 1,
      caseCount: 0,
      runnable: false,
      runAllEligible: false,
      command: '',
      tags: [manual ? 'manual' : 'support', 'frontend'],
    });
  }

  const generatedSuites = walkFiles(path.join(root, 'bash_tests'), file => file.endsWith('.sh')).map(file => {
    const source = fs.readFileSync(file, 'utf8');
    const sourcePath = relative(root, file);
    const caseCount = source.match(/^run_request\s+/gm)?.length || 0;
    const mode = /FLOW Execution/i.test(source) ? 'Flow' : /Independent Execution/i.test(source) ? 'Independent' : 'Generated';
    return {
      id: `generated-shell:${sourcePath}`,
      project,
      repository: path.basename(root),
      recordType: 'GENERATED_SUITE',
      suite: `CAPI ${mode}`,
      name: path.basename(file, '.sh'),
      displayName: `${path.basename(file, '.sh')} (${caseCount.toLocaleString('en-US')} requests)`,
      framework: 'Generated Bash / curl',
      language: 'Shell',
      kind: 'API_GENERATED',
      runtime: 'LIVE_API',
      execution: 'MANUAL_ONLY',
      safety: 'LIVE_EXTERNAL',
      sourcePath,
      line: 1,
      caseCount,
      runnable: false,
      runAllEligible: false,
      command: '',
      tags: ['generated', 'api', mode.toLowerCase()],
    };
  });

  return { testFiles, tests, artifacts, generatedSuites };
}

function ensureUniqueIds(entries) {
  const seen = new Set();
  for (const entry of entries) {
    let candidate = entry.id;
    let suffix = 2;
    while (seen.has(candidate)) candidate = `${entry.id}:${suffix++}`;
    entry.id = candidate;
    seen.add(candidate);
  }
}

const scanner = discoverJavaProject('AR Web Scanner', scannerRoot);
const react = await discoverReactProject('AR React UI', reactRoot);
const engineTestRoot = path.join(engineRoot, 'src', 'test');
const engineTestFiles = walkFiles(engineTestRoot, file => /\.(java|kt|groovy)$/.test(file));

const entries = [
  ...scanner.tests,
  ...react.tests,
  ...react.generatedSuites,
  ...scanner.artifacts,
  ...react.artifacts,
];
ensureUniqueIds(entries);
entries.sort((left, right) =>
  (projectOrder.get(left.project) ?? 99) - (projectOrder.get(right.project) ?? 99)
  || left.sourcePath.localeCompare(right.sourcePath)
  || left.line - right.line
  || left.displayName.localeCompare(right.displayName));

const automatedCodeCases = entries
  .filter(entry => entry.recordType === 'AUTOMATED_CASE')
  .reduce((sum, entry) => sum + entry.caseCount, 0);
const generatedApiCases = entries
  .filter(entry => entry.recordType === 'GENERATED_SUITE')
  .reduce((sum, entry) => sum + entry.caseCount, 0);
const manualArtifacts = entries.filter(entry => entry.recordType === 'MANUAL_TOOL').length;
const supportArtifacts = entries.filter(entry => entry.recordType === 'SUPPORT').length;
const automatedSuites = new Set(entries
  .filter(entry => entry.recordType === 'AUTOMATED_CASE')
  .map(entry => `${entry.project}:${entry.sourcePath}`)).size;

const scannerMetadata = repoMetadata('AR Web Scanner', scannerRoot);
const reactMetadata = repoMetadata('AR React UI', reactRoot);
const engineMetadata = repoMetadata('AR Web Engine', engineRoot);
const document = {
  schemaVersion: 1,
  generatedAt: new Date().toISOString(),
  summary: {
    catalogEntries: entries.length,
    automatedCodeCases,
    generatedApiCases,
    totalAutomatedCases: automatedCodeCases + generatedApiCases,
    automatedSuites,
    generatedSuites: react.generatedSuites.length,
    manualArtifacts,
    supportArtifacts,
    defaultRunnable: entries.filter(entry => entry.recordType === 'AUTOMATED_CASE' && entry.execution === 'DEFAULT').length,
    safeRunAllEligible: entries.filter(entry => entry.runAllEligible).length,
  },
  sources: [
    {
      ...scannerMetadata,
      sourceFiles: scanner.javaFiles.length,
      automatedSuites: new Set(scanner.tests.map(test => test.sourcePath)).size,
      automatedTestCases: scanner.tests.length,
      generatedSuites: 0,
      generatedCases: 0,
    },
    {
      ...reactMetadata,
      sourceFiles: react.testFiles.length,
      automatedSuites: react.testFiles.length,
      automatedTestCases: react.tests.length,
      generatedSuites: react.generatedSuites.length,
      generatedCases: react.generatedSuites.reduce((sum, suite) => sum + suite.caseCount, 0),
    },
    {
      ...engineMetadata,
      sourceFiles: engineTestFiles.length,
      automatedSuites: 0,
      automatedTestCases: 0,
      generatedSuites: 0,
      generatedCases: 0,
      note: 'Production Selenium engine; no committed automated test source tree. Local/live launch profiles are manual and excluded from Run All.',
    },
  ],
  tests: entries,
};

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, `${JSON.stringify(document, null, 2)}\n`, 'utf8');
process.stdout.write(
  `Wrote ${relative(scannerRoot, outputPath)} with ${entries.length} rows, `
  + `${automatedCodeCases} code cases, and ${generatedApiCases} generated API requests.\n`,
);
