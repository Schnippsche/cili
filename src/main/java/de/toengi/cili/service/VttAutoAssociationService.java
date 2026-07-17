package de.toengi.cili.service;

import de.toengi.cili.config.WhisperConfig;
import de.toengi.cili.model.entity.Resource;
import de.toengi.cili.model.entity.SubtitleTrack;
import de.toengi.cili.model.enums.SubtitleFormat;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.repository.SubtitleTrackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VttAutoAssociationService {

    private final ResourceRepository resourceRepository;
    private final SubtitleTrackRepository subtitleTrackRepository;
    private final ProcessingJobService jobService;
    private final WhisperConfig whisperConfig;

    @Transactional
    public void onVttUploaded(Long vttResourceId, String originalName, Long folderId) {
        VttFileInfo info = VttFileInfo.parse(originalName);
        if (info == null) return;

        String language = info.language() != null ? info.language() : whisperConfig.getLanguage();
        String prefix = info.baseName() + ".%";

        List<Resource> videos = resourceRepository.findInFolderByNamePrefix(folderId, prefix, "video/%");
        for (Resource video : videos) {
            if (!subtitleTrackRepository.existsByResourceIdAndLanguageCode(video.getId(), language)) {
                Resource vttResource = resourceRepository.findById(vttResourceId).orElse(null);
                if (vttResource == null) continue;

                SubtitleTrack track = SubtitleTrack.builder()
                    .resourceId(video.getId())
                    .languageCode(language)
                    .label("Auto (" + language + ")")
                    .storedName(vttResource.getStoredName())
                    .format(SubtitleFormat.VTT)
                    .build();
                subtitleTrackRepository.save(track);
                log.info("Auto-associated VTT '{}' (lang={}) with video resource {}",
                    originalName, language, video.getId());

                jobService.cancelWhisperJobIfActive(video.getId());
            }
        }
    }

    @Transactional
    public boolean onVideoUploaded(Long videoResourceId, String originalName, Long folderId) {
        String baseName = videoBaseName(originalName);
        String prefix = baseName + ".%";

        List<Resource> vtts = resourceRepository.findInFolderByNamePrefix(folderId, prefix, "text/vtt%");
        boolean anyAssociated = false;
        for (Resource vtt : vtts) {
            VttFileInfo info = VttFileInfo.parse(vtt.getOriginalName());
            if (info == null) continue;
            String language = info.language() != null ? info.language() : whisperConfig.getLanguage();

            if (!subtitleTrackRepository.existsByResourceIdAndLanguageCode(videoResourceId, language)) {
                SubtitleTrack track = SubtitleTrack.builder()
                    .resourceId(videoResourceId)
                    .languageCode(language)
                    .label("Auto (" + language + ")")
                    .storedName(vtt.getStoredName())
                    .format(SubtitleFormat.VTT)
                    .build();
                subtitleTrackRepository.save(track);
                log.info("Auto-associated existing VTT '{}' (lang={}) with newly uploaded video {}",
                    vtt.getOriginalName(), language, videoResourceId);
                anyAssociated = true;
            }
        }
        return anyAssociated;
    }

    static String videoBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public record VttFileInfo(String baseName, String language) {
        public static VttFileInfo parse(String fileName) {
            if (fileName == null || !fileName.toLowerCase().endsWith(".vtt")) return null;
            String withoutExt = fileName.substring(0, fileName.length() - 4);
            int lastDot = withoutExt.lastIndexOf('.');
            if (lastDot > 0) {
                String possibleLang = withoutExt.substring(lastDot + 1);
                if (possibleLang.matches("[a-zA-Z]{2,3}")) {
                    return new VttFileInfo(withoutExt.substring(0, lastDot), possibleLang.toLowerCase());
                }
            }
            return new VttFileInfo(withoutExt, null);
        }
    }
}
