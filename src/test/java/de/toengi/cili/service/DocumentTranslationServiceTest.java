package de.toengi.cili.service;

import de.toengi.cili.config.CiliGlobalConfig;
import de.toengi.cili.config.FileStorageConfig;
import de.toengi.cili.config.NllbConfig;
import de.toengi.cili.repository.ProcessingJobRepository;
import de.toengi.cili.repository.ResourceMetadataRepository;
import de.toengi.cili.repository.ResourceRepository;
import de.toengi.cili.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import static java.nio.file.Path.of;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DocumentTranslationServiceTest {

    @Mock ProcessingJobService jobService;
    @Mock ProcessingJobRepository processingJobRepository;
    @Mock ResourceRepository resourceRepository;
    @Mock ResourceMetadataRepository metadataRepository;
    @Mock StorageService storageService;
    @Mock LanguageOptionService languageOptionService;
    @Mock Executor translationExecutor;

    NllbConfig nllbConfig;
    CiliGlobalConfig global;
    FileStorageConfig storageConfig;
    DocumentTranslationService service;

    @BeforeEach
    void setUp() {
        nllbConfig = new NllbConfig();
        nllbConfig.setScriptName("translate_worker.py");
        nllbConfig.setDocScriptName("doc_translate_worker.py");
        nllbConfig.setModelPath("/opt/cili/nllb/nllb-600M");
        nllbConfig.setDevice("cpu");
        nllbConfig.setComputeType("int8");
        nllbConfig.setBeamSize(4);
        nllbConfig.setTimeoutMinutes(10);

        global = new CiliGlobalConfig();
        global.setPythonPath("python3");
        global.setScriptsDir("/opt/cili/scripts");

        storageConfig = new FileStorageConfig();
        storageConfig.setLibreOfficePath("/usr/bin/soffice");

        service = new DocumentTranslationService(
            jobService, processingJobRepository, resourceRepository,
            metadataRepository, storageService, nllbConfig, global,
            storageConfig, translationExecutor, languageOptionService);
    }

    @Test
    void buildCommand_usesGlobalPythonPath() {
        global.setPythonPath("/usr/bin/python3.11");
        List<String> cmd = service.buildCommand(
            Path.of("/tmp/in.pdf"), Path.of("/tmp/out.txt"), "de", "en");
        assertThat(cmd.get(0)).isEqualTo("/usr/bin/python3.11");
    }

    @Test
    void buildCommand_usesDocScriptPath() {
        List<String> cmd = service.buildCommand(
            Path.of("/tmp/in.pdf"), Path.of("/tmp/out.txt"), "de", "en");
        assertThat(cmd.get(1)).isEqualTo(of("/opt/cili/scripts", "doc_translate_worker.py").toString());
    }

    @Test
    void buildCommand_containsAllRequiredArguments() {
        Path in  = Path.of("/tmp/in.pdf");
        Path out = Path.of("/tmp/out.txt");
        List<String> cmd = service.buildCommand(in, out, "de", "en");

        assertThat(cmd)
                .containsSequence("--input",        in.toString())
                .containsSequence("--output",       out.toString())
                .containsSequence("--source",       "de")
                .containsSequence("--target",       "en")
                .containsSequence("--model",        "/opt/cili/nllb/nllb-600M")
                .containsSequence("--libreoffice",  "/usr/bin/soffice")
                .containsSequence("--device",       "cpu")
                .containsSequence("--compute-type", "int8")
                .containsSequence("--beam-size",    "4");
    }

    @Test
    void buildOutputName_appendsLangAndTxtExtension() {
        assertThat(service.buildOutputName("Report.pdf",        "en")).isEqualTo("Report.en.txt");
        assertThat(service.buildOutputName("Präsentation.pdf",  "de")).isEqualTo("Präsentation.de.txt");
        assertThat(service.buildOutputName("file.with.dots.pdf","fr")).isEqualTo("file.with.dots.fr.txt");
        assertThat(service.buildOutputName("nodot",              "pl")).isEqualTo("nodot.pl.txt");
        assertThat(service.buildOutputName("Contract.docx",     "en")).isEqualTo("Contract.en.txt");
        assertThat(service.buildOutputName("Notes.txt",         "fr")).isEqualTo("Notes.fr.txt");
    }
}
