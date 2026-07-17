package de.toengi.cili.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RangeUtilsTest {

    @Test
    void noRangeHeader_returnsNull() {
        assertThat(RangeUtils.parseFirstRange(null, 1000)).isNull();
        assertThat(RangeUtils.parseFirstRange("", 1000)).isNull();
        assertThat(RangeUtils.parseFirstRange("invalid", 1000)).isNull();
    }

    @Test
    void explicitRange_returnsCorrectBounds() {
        long[] r = RangeUtils.parseFirstRange("bytes=0-499", 1000);
        assertThat(r).isEqualTo(new long[]{0, 499});
        assertThat(RangeUtils.length(r)).isEqualTo(500);
    }

    @Test
    void openEndRange_clampsToDefaultChunkOrEnd() {
        long[] r = RangeUtils.parseFirstRange("bytes=0-", 500);
        // open end with totalLength 500 < DEFAULT_CHUNK → clamps to end
        assertThat(r[0]).isZero();
        assertThat(r[1]).isEqualTo(499);
    }

    @Test
    void suffixRange_returnsLastNBytes() {
        long[] r = RangeUtils.parseFirstRange("bytes=-200", 1000);
        assertThat(r).isEqualTo(new long[]{800, 999});
    }

    @Test
    void startBeyondTotal_returnsNull() {
        assertThat(RangeUtils.parseFirstRange("bytes=1001-1500", 1000)).isNull();
    }

    @Test
    void endClampsToTotalMinusOne() {
        long[] r = RangeUtils.parseFirstRange("bytes=900-9999", 1000);
        assertThat(r).isEqualTo(new long[]{900, 999});
    }

    @Test
    void startEqualsEnd_singleByte() {
        long[] r = RangeUtils.parseFirstRange("bytes=5-5", 100);
        assertThat(r).isEqualTo(new long[]{5, 5});
        assertThat(RangeUtils.length(r)).isEqualTo(1);
    }

    @Test
    void malformedRange_returnsNull() {
        // bytes=-5- → endStr="5-" → NumberFormatException → null
        assertThat(RangeUtils.parseFirstRange("bytes=-5-", 1000)).isNull();
        // bytes=abc-10 → startStr="abc" → NumberFormatException → null
        assertThat(RangeUtils.parseFirstRange("bytes=abc-10", 1000)).isNull();
    }

    @Test
    void zeroLengthFile_returnsNull() {
        assertThat(RangeUtils.parseFirstRange("bytes=0-0", 0)).isNull();
    }

    @Test
    void suffixLargerThanFile_clampsToStart() {
        long[] r = RangeUtils.parseFirstRange("bytes=-5000", 100);
        // start = max(0, 100-5000) = 0, end = 99
        assertThat(r).isEqualTo(new long[]{0, 99});
    }
}
