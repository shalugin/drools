package demo.drools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

/**
 * @author shalugin
 */
public class DirWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(DirWatcher.class);

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private final boolean recursive;

    /**
     * Creates a WatchService and registers the given directory
     */
    public DirWatcher(Path dir, boolean recursive) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new ConcurrentHashMap<>();
        this.recursive = recursive;

        if (recursive) {
            LOG.info("Scanning {} ...", dir);
            registerAll(dir);
            LOG.info("Done.");
        } else {
            register(dir);
        }
    }

    public void closeWatcher() throws IOException {
        this.watcher.close();
    }

    public boolean processEvents(long timeoutInMs) {
        // wait for key to be signalled
        WatchKey key;
        try {
            key = watcher.poll(timeoutInMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException x) {
            return false;
        }

        if (key == null) {
            return false;
        }

        Path dir = keys.get(key);
        if (dir == null) {
            LOG.error("WatchKey not recognized!!");
            return false;
        }

        boolean changesDetected = false;

        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind kind = event.kind();

            if (kind == OVERFLOW) {
                LOG.error("OVERFLOW kind. Processing next record ...");
                continue;
            }

            // Context for directory entry event is the file name of entry
            @SuppressWarnings("unchecked") WatchEvent<Path> ev = (WatchEvent<Path>) event;
            Path name = ev.context();
            Path child = dir.resolve(name);

            // пока просто логируем изменения в файловой системе
            // если нужно будет анализировать каждый изменённый файл - можно в функцию прокинуть callback,
            // или генерировать из функции CDI Event<>
            LOG.info("{} {}", event.kind().name(), child);
            changesDetected = true;

            // if directory is created, and watching recursively, then
            // register it and its sub-directories
            if (recursive && (kind == ENTRY_CREATE)) {
                try {
                    if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                        registerAll(child);
                    }
                } catch (IOException e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        }

        // reset key and remove from set if directory no longer accessible
        boolean valid = key.reset();
        if (!valid) {
            keys.remove(key);
        }
        return changesDetected;
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }
}
