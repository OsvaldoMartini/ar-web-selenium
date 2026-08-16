import assert from 'node:assert/strict';
import test from 'node:test';
import vm from 'node:vm';
import { scannerEvaluationExpression } from '../src/browser/playwrightBrowserFactory';

test('scanner evaluation invokes a no-argument function and returns its structured result', () => {
  const expression = scannerEvaluationExpression(
    '() => ({ material: "fingerprint", nodeCount: 7 })',
    undefined,
    false,
  );
  assert.deepEqual(
    JSON.parse(JSON.stringify(vm.runInNewContext(expression))),
    { material: 'fingerprint', nodeCount: 7 },
  );
});

test('scanner evaluation preserves a JSON-safe argument and array result', () => {
  const argument = { selector: 'button[data-name="A B"]', values: ['one', 'two'] };
  const expression = scannerEvaluationExpression(
    '(input) => [input.selector, ...input.values]',
    argument,
    true,
  );
  assert.deepEqual(
    Array.from(vm.runInNewContext(expression)),
    ['button[data-name="A B"]', 'one', 'two'],
  );
});

test('scanner evaluation retains non-function expression compatibility', () => {
  assert.equal(vm.runInNewContext(scannerEvaluationExpression('21 * 2', undefined, false)), 42);
});
