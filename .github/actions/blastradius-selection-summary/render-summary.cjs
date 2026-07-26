const COMMENT_MARKER = '<!-- blastradius-selection-summary -->';

function readCount(report, key, required = false) {
  const value = report[key];
  if (value === undefined && !required) return undefined;
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new Error(`${key} must be a non-negative integer`);
  }
  return value;
}

function displayReason(reason) {
  const knownReasons = {
    DEPENDENCY_MATCH: 'Dependency match',
    NO_MATCH: 'No match',
    NEW_OR_MODIFIED_TEST: 'New or modified test',
    FALLBACK_NON_SOURCE_CHANGE: 'Fallback non-source change',
  };
  return knownReasons[reason]
    ?? reason.toLowerCase().split('_').map((word) => word[0].toUpperCase() + word.slice(1)).join(' ');
}

function reasonCounts(report) {
  if (report.reasonCounts !== undefined) {
    if (typeof report.reasonCounts !== 'object' || report.reasonCounts === null || Array.isArray(report.reasonCounts)) {
      throw new Error('reasonCounts must be an object');
    }
    return Object.entries(report.reasonCounts)
      .map(([reason, count]) => [reason, readCount(report.reasonCounts, reason, true)])
      .filter(([, count]) => count > 0);
  }

  const counts = new Map();
  for (const decision of report.decisions ?? []) {
    if (typeof decision?.reason === 'string') {
      counts.set(decision.reason, (counts.get(decision.reason) ?? 0) + 1);
    }
  }
  return [...counts.entries()];
}

function formatDuration(milliseconds) {
  const seconds = Math.round(milliseconds / 1000);
  const minutes = Math.floor(seconds / 60);
  const remainderSeconds = seconds % 60;
  return minutes === 0 ? `~${remainderSeconds}s` : `~${minutes}m ${remainderSeconds}s`;
}

function renderSelectionSummary(report) {
  if (typeof report !== 'object' || report === null || Array.isArray(report)) {
    throw new Error('report must be an object');
  }

  const selectedCount = readCount(report, 'selectedCount', true);
  const totalCount = readCount(report, 'totalCount', true);
  if (selectedCount > totalCount) throw new Error('selectedCount must not exceed totalCount');

  const derivedSkippedCount = totalCount - selectedCount;
  const skippedCount = readCount(report, 'skippedCount') ?? derivedSkippedCount;
  if (skippedCount !== derivedSkippedCount) throw new Error('skippedCount must equal totalCount minus selectedCount');

  const estimate = report.estimatedTimeSavedMillis;
  if (estimate !== undefined && estimate !== null && (!Number.isSafeInteger(estimate) || estimate < 0)) {
    throw new Error('estimatedTimeSavedMillis must be a non-negative integer or null');
  }

  const savings = estimate === undefined || estimate === null ? null : formatDuration(estimate);
  const lines = [
    COMMENT_MARKER,
    '## Blastradius selection',
    '',
    savings
      ? `Ran ${selectedCount}/${totalCount}, skipped ${skippedCount}, ${savings} saved.`
      : `Ran ${selectedCount}/${totalCount}, skipped ${skippedCount}.`,
  ];

  if (!savings) lines.push('', 'Timing history is incomplete, so no time-saved estimate is shown.');

  const reasons = reasonCounts(report);
  if (reasons.length > 0) {
    lines.push('', '### Reasons', '');
    for (const [reason, count] of reasons.sort(([left], [right]) => left.localeCompare(right))) {
      lines.push(`- ${displayReason(reason)}: ${count}`);
    }
  }

  return `${lines.join('\n')}\n`;
}

module.exports = { COMMENT_MARKER, renderSelectionSummary };
