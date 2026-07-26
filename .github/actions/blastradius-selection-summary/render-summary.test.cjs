const assert = require('node:assert/strict');
const test = require('node:test');

const { combineSelectionReports, renderSelectionSummary } = require('./render-summary.cjs');

test('combines the module reports for a reactor-wide summary', () => {
  assert.deepEqual(combineSelectionReports([
    {
      selectedCount: 2,
      totalCount: 5,
      estimatedTimeSavedMillis: 1200,
      reasonCounts: { DEPENDENCY_MATCH: 2, NO_MATCH: 3 },
    },
    {
      selectedCount: 1,
      totalCount: 4,
      estimatedTimeSavedMillis: 800,
      reasonCounts: { NEW_OR_MODIFIED_TEST: 1, NO_MATCH: 3 },
    },
  ]), {
    selectedCount: 3,
    totalCount: 9,
    skippedCount: 6,
    estimatedTimeSavedMillis: 2000,
    reasonCounts: { DEPENDENCY_MATCH: 2, NO_MATCH: 6, NEW_OR_MODIFIED_TEST: 1 },
  });
});

test('renders the selection result, timing estimate, and non-zero reasons', () => {
  const rendered = renderSelectionSummary({
    selectedCount: 42,
    totalCount: 310,
    skippedCount: 268,
    estimatedTimeSavedMillis: 365000,
    reasonCounts: {
      DEPENDENCY_MATCH: 42,
      NO_MATCH: 268,
      NEW_OR_MODIFIED_TEST: 0,
    },
  });

  assert.match(rendered, /Ran 42\/310, skipped 268, ~6m 5s saved\./);
  assert.match(rendered, /Dependency match: 42/);
  assert.match(rendered, /No match: 268/);
  assert.doesNotMatch(rendered, /New or modified test/);
});

test('derives skipped tests for an older report and withholds an incomplete estimate', () => {
  const rendered = renderSelectionSummary({
    selectedCount: 2,
    totalCount: 5,
    decisions: [
      { reason: 'DEPENDENCY_MATCH' },
      { reason: 'NO_MATCH' },
    ],
  });

  assert.match(rendered, /Ran 2\/5, skipped 3\./);
  assert.match(rendered, /Timing history is incomplete, so no time-saved estimate is shown\./);
  assert.match(rendered, /Dependency match: 1/);
  assert.match(rendered, /No match: 1/);
});

test('rejects an invalid report rather than publishing misleading counts', () => {
  assert.throws(
    () => renderSelectionSummary({ selectedCount: 4, totalCount: 3 }),
    /selectedCount must not exceed totalCount/,
  );
});
