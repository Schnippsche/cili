import { findCueIndexForTimestamp, type Cue } from './TranscriptPanel';

// Cues from the reported bug (00:17:38.500 … 00:18:01.760)
const cues: Cue[] = [
  { startTime: 1058.5,  endTime: 1065.38, text: 'A' },
  { startTime: 1065.38, endTime: 1076.02, text: 'B' },   // index 1
  { startTime: 1076.02, endTime: 1081.76, text: 'C' },   // index 2 (target)
];

describe('findCueIndexForTimestamp', () => {
  it('selects the cue starting at the truncated timestamp, not the previous one', () => {
    // Backend truncates 1076.02 → 1076 (SearchService.toSeconds)
    expect(findCueIndexForTimestamp(cues, 1076)).toBe(2);
  });

  it('selects the containing cue for a mid-cue timestamp', () => {
    expect(findCueIndexForTimestamp(cues, 1078)).toBe(2);
  });

  it('handles whole-second cue starts', () => {
    const whole: Cue[] = [
      { startTime: 10, endTime: 20, text: 'A' },
      { startTime: 20, endTime: 30, text: 'B' },
    ];
    expect(findCueIndexForTimestamp(whole, 20)).toBe(1);
  });

  it('returns the last cue when timestamp is past the end', () => {
    expect(findCueIndexForTimestamp(cues, 9999)).toBe(2);
  });

  it('returns -1 for empty cue list', () => {
    expect(findCueIndexForTimestamp([], 1076)).toBe(-1);
  });
});
