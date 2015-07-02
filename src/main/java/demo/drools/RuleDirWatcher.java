package demo.drools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author shalugin
 */
@Component
public class RuleDirWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(RuleDirWatcher.class);

    private DirWatcher dirWatcher;
    private boolean enabled = false;

    @Autowired
    private KieContainerGenerator kieContainerGenerator;

    @PostConstruct
    public void postConstruct() {
        try {
            String rulesFolder = RuleUtil.getRulesFolder();
            if (rulesFolder == null) {
                return;
            }

            Path path = Paths.get(rulesFolder);
            dirWatcher = new DirWatcher(path, true);
            LOG.info("Start monitoring folder {}", path);
            enabled = true;

        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @PreDestroy
    public void preDestroy() {
        try {
            if (enabled) {
                dirWatcher.closeWatcher();
            }
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 5000)
    public void watch() {
        if (!enabled) {
            return;
        }

        try {
            if (dirWatcher.processEvents(1000L)) {
                kieContainerGenerator.generateNewFileSystem();
            }
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }
    }

}
