package de.toengi.cili.service;

import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.config.FfmpegTranscodeConfig;
import de.toengi.cili.dto.video.VideoAnalysisResult;
import de.toengi.cili.dto.video.VideoAnalysisResult.TranscodeAction;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

class VideoAnalysisServiceTest {

    @Mock ResourceRepository resourceRepo;
    @Mock StorageService storageService;
    @Mock ProcessingJobService jobService;
    FfmpegTranscodeConfig ffmpegConfig;
    FileStorageConfig storageConfig;
    VideoAnalysisService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ffmpegConfig = new FfmpegTranscodeConfig();
        ffmpegConfig.setTargetHeight(720);
        storageConfig = new FileStorageConfig();
        storageConfig.setFfprobePath("/usr/bin/ffprobe");
        service = new VideoAnalysisService(
            resourceRepo, storageService, jobService,
            ffmpegConfig, storageConfig);
    }

    @Test
    void analyze_1080pH264_recommendsTranscode() throws Exception {
        String ffprobeJson = """
            {
              "streams": [
                {"codec_type":"video","codec_name":"h264","width":1920,"height":1080},
                {"codec_type":"audio","codec_name":"aac"}
              ],
              "format": {"bit_rate":"8000000","duration":"60.0"}
            }""";

        VideoAnalysisResult result = service.parseAnalysisOutput(ffprobeJson, ffmpegConfig);

        assertThat(result.height()).isEqualTo(1080);
        assertThat(result.width()).isEqualTo(1920);
        assertThat(result.videoCodec()).isEqualTo("h264");
        assertThat(result.action()).isEqualTo(TranscodeAction.TRANSCODE);
    }

    @Test
    void analyze_720pH264Video_recommendsSkip() throws Exception {
        String ffprobeJson = """
            {
              "streams": [
                {"codec_type":"video","codec_name":"h264","width":1280,"height":720},
                {"codec_type":"audio","codec_name":"aac"}
              ],
              "format": {"bit_rate":"3000000","duration":"60.0"}
            }""";

        VideoAnalysisResult result = service.parseAnalysisOutput(ffprobeJson, ffmpegConfig);

        assertThat(result.action()).isEqualTo(TranscodeAction.SKIP);
    }

    @Test
    void analyze_720pHevcVideo_recommendsTranscode() throws Exception {
        String ffprobeJson = """
            {
              "streams": [
                {"codec_type":"video","codec_name":"hevc","width":1280,"height":720},
                {"codec_type":"audio","codec_name":"aac"}
              ],
              "format": {"bit_rate":"2000000","duration":"60.0"}
            }""";

        VideoAnalysisResult result = service.parseAnalysisOutput(ffprobeJson, ffmpegConfig);

        assertThat(result.action()).isEqualTo(TranscodeAction.TRANSCODE);
    }

    @Test
    void analyze_720pH264_heAacAudio_recommendsTranscode() throws Exception {
        String ffprobeJson = """
            {
              "streams": [
                {"codec_type":"video","codec_name":"h264","width":1280,"height":720},
                {"codec_type":"audio","codec_name":"aac","profile":"HE-AAC"}
              ],
              "format": {"bit_rate":"2000000","duration":"60.0"}
            }""";

        VideoAnalysisResult result = service.parseAnalysisOutput(ffprobeJson, ffmpegConfig);

        assertThat(result.action()).isEqualTo(TranscodeAction.TRANSCODE);
    }

    @Test
    void analyze_720pH264_heAacV2Audio_recommendsTranscode() throws Exception {
        String ffprobeJson = """
            {
              "streams": [
                {"codec_type":"video","codec_name":"h264","width":1280,"height":720},
                {"codec_type":"audio","codec_name":"aac","profile":"HE-AAC v2"}
              ],
              "format": {"bit_rate":"2000000","duration":"60.0"}
            }""";

        VideoAnalysisResult result = service.parseAnalysisOutput(ffprobeJson, ffmpegConfig);

        assertThat(result.action()).isEqualTo(TranscodeAction.TRANSCODE);
    }

    @Test
    void analyze_720pH264_aacLcAudio_recommendsSkip() throws Exception {
        String ffprobeJson = """
            {
              "streams": [
                {"codec_type":"video","codec_name":"h264","width":1280,"height":720},
                {"codec_type":"audio","codec_name":"aac","profile":"LC"}
              ],
              "format": {"bit_rate":"2000000","duration":"60.0"}
            }""";

        VideoAnalysisResult result = service.parseAnalysisOutput(ffprobeJson, ffmpegConfig);

        assertThat(result.action()).isEqualTo(TranscodeAction.SKIP);
    }
}
