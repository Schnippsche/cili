package de.toengi.cili.util;

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface CommandRunner {
    int run(List<String> command) throws IOException, InterruptedException;
}
