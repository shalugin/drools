package demo.drools;

import org.drools.compiler.kproject.ReleaseIdImpl;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;

/**
 * @author shalugin
 */
@Component
public class KieContainerGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(KieContainerGenerator.class);
    private static final String RULES_RELATIVE_DIR = "/rules";
    private static final String JAR_RESOURCE_DIR = "src/main/resources/";

    private final KieServices kieServices = KieServices.Factory.get();
    private ReleaseId releaseId;

    @PostConstruct
    public void postConstruct() {
        try {
            releaseId = generateReleaseId();
            generateNewFileSystem(releaseId);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public KieContainer getDefaultContainer() {
        return kieServices.newKieContainer(releaseId);
    }

    public void generateNewFileSystem() throws IOException {
        ReleaseId releaseId = generateReleaseId();
        generateNewFileSystem(releaseId);
        changeDefaultKieModule(releaseId);
    }

    private void generateNewFileSystem(ReleaseId releaseId) throws IOException {
        KieFileSystem kfs = getKieFileSystemWithFiles(releaseId);

        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();
        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            LOG.error("Error building new kie file system: {}", results);
            return;
        } else if (results.hasMessages(Message.Level.WARNING)) {
            LOG.error("Warnings detected while building new kie file system: {}", results);
        }

        LOG.info("New kie file system built. ReleaseId: {}", releaseId);
    }

    private KieFileSystem getKieFileSystemWithFiles(ReleaseId releaseId) throws IOException {
        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.generateAndWritePomXML(releaseId);

        // сперва все файлы из classpath
        loadFilesFromClassPath(kfs);

        // затем все файлы из деректории с правилами
        String rulesFolder = RuleUtil.getRulesFolder();
        if (rulesFolder != null) {
            File folder = new File(rulesFolder);
            addAllFilesInDirectory(kfs, folder, folder);
        }

        return kfs;
    }

    private void loadFilesFromClassPath(KieFileSystem kfs) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String parentFolder = resolver.getResource("classpath:" + RULES_RELATIVE_DIR).getURL().getPath();
        org.springframework.core.io.Resource[] resources = resolver.getResources("classpath:" + RULES_RELATIVE_DIR + "/**/*.*");
        if (resources != null) {
            for (org.springframework.core.io.Resource resource : resources) {
                String relativePath = JAR_RESOURCE_DIR + FileUtil.getRelativePath(resource.getURL().getPath(), parentFolder);

                try (InputStream inputStream = resource.getInputStream()) {
                    addFile(kfs, relativePath, inputStream);
                }
            }
        }
    }

    private void addAllFilesInDirectory(KieFileSystem kfs, File folder, File parentDir) throws IOException {
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null) {
            return;
        }

        for (File file : listOfFiles) {
            if (file.isFile()) {
                String relativePath = JAR_RESOURCE_DIR +
                        FileUtil.getRelativePath(file.getAbsolutePath(), parentDir.getAbsolutePath());
                String absolutePath = file.getAbsolutePath();
                addFile(kfs, absolutePath, relativePath);
                LOG.info("Added file: {} as {}.", absolutePath, relativePath);
            } else if (file.isDirectory()) {
                addAllFilesInDirectory(kfs, file, parentDir);
            }
        }
    }

    private void addFile(KieFileSystem kfs, String name, String kfsName) throws IOException {
        try (InputStream fis = new FileInputStream(name)) {
            addFile(kfs, kfsName, fis);
        }
    }

    private void addFile(KieFileSystem kfs, String kfsName, InputStream fis) {
        kfs.write(kfsName, kieServices.getResources().newInputStreamResource(fis, "UTF-8"));
    }

    private void changeDefaultKieModule(ReleaseId releaseId) {
        ReleaseId oldReleaseId = this.releaseId;
        this.releaseId = releaseId;
        kieServices.getRepository().removeKieModule(oldReleaseId);
    }

    private ReleaseId generateReleaseId() {
        String version = "" + System.currentTimeMillis();
        return new ReleaseIdImpl("demo.drools", "rules", version);
    }

    public void validateRules(String ruleSet, Map<String, Object> globals, Object[] workingMemory) {
        KieContainer container = getDefaultContainer();
        StatelessKieSession session = container.newStatelessKieSession(ruleSet);

        for (Map.Entry<String, Object> e : globals.entrySet()) {
            session.setGlobal(e.getKey(), e.getValue());
        }

        session.execute(Arrays.asList(workingMemory));
    }

}
