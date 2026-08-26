/*
* Copyright 2020 Expedia, Inc.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.vrbo.jarviz.util;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import static com.vrbo.jarviz.util.FileReadWriteUtils.getOrCreateDirectory;
import static com.vrbo.jarviz.util.FileReadWriteUtils.readFileAsString;
import static com.vrbo.jarviz.util.FileReadWriteUtils.readResourceAsString;
import static com.vrbo.jarviz.util.FileReadWriteUtils.toFullPath;
import static com.vrbo.jarviz.util.FileReadWriteUtils.writeToFile;

public class FileReadWriteUtilsTest {

    private static final String TEST_RESOURCE = "file-read-write-utils-test.txt";

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testToFullPath() {
        assertThat(toFullPath("/tmp/jarviz/jars", "fooBar123.jar"))
            .isEqualTo("/tmp/jarviz/jars" + File.separator + "fooBar123.jar");

        assertThat(toFullPath("/tmp/jarviz/jars/", "fooBar123.jar"))
            .isEqualTo("/tmp/jarviz/jars" + File.separator + "fooBar123.jar");
    }

    @Test
    public void testToFullPath_ValidationFailure() {
        final String oneLevelUp = File.separator + ".." + File.separator;

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> toFullPath("/tmp/jarviz/jars", oneLevelUp + "fooBar123.jar"));
    }

    @Test
    public void testToFullPath_ValidationFailureForNestedTraversal() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> toFullPath("/tmp/jarviz/jars", "sub" + File.separator + ".."
                                                            + File.separator + ".." + File.separator + "evil.jar"));
    }

    @Test
    public void testToFullPath_SubDirectoryIsAllowed() {
        assertThat(toFullPath("/tmp/jarviz/jars", "sub" + File.separator + "fooBar123.jar"))
            .isEqualTo("/tmp/jarviz/jars" + File.separator + "sub" + File.separator + "fooBar123.jar");
    }

    @Test
    public void testToFullPath_EmptyFileName() {
        assertThat(toFullPath("/tmp/jarviz/jars", "")).isEqualTo("/tmp/jarviz/jars" + File.separator);
    }

    @Test
    public void testGetOrCreateDirectory_CreatesNestedDirectories() {
        final File dir = new File(tempFolder.getRoot(), "a" + File.separator + "b" + File.separator + "c");
        assertThat(dir).doesNotExist();

        final File created = getOrCreateDirectory(dir.getPath());

        assertThat(created).exists();
        assertThat(created.isDirectory()).isTrue();
    }

    @Test
    public void testGetOrCreateDirectory_IsIdempotent() throws IOException {
        final File dir = tempFolder.newFolder("existing");

        assertThat(getOrCreateDirectory(dir.getPath())).isEqualTo(dir);
        assertThat(getOrCreateDirectory(dir.getPath())).isEqualTo(dir);
    }

    @Test
    public void testGetOrCreateDirectory_FailsWhenPathIsARegularFile() throws IOException {
        final File file = tempFolder.newFile("not-a-dir.txt");

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> getOrCreateDirectory(file.getPath()))
            .withMessageContaining("File should be a directory");
    }

    @Test
    public void testWriteToFile() throws IOException {
        final File file = new File(tempFolder.getRoot(), "out.txt");

        writeToFile(file.getPath(), writer -> write(writer, "Hello Ünïcødé 日本語\nsecond line\n"));

        assertThat(readFileAsString(file)).isEqualTo("Hello Ünïcødé 日本語\nsecond line\n");
        assertThat(Files.readAllBytes(file.toPath()))
            .isEqualTo("Hello Ünïcødé 日本語\nsecond line\n".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testWriteToFile_TruncatesExistingContent() throws IOException {
        final File file = tempFolder.newFile("overwrite.txt");
        Files.write(file.toPath(), "old and much longer content".getBytes(StandardCharsets.UTF_8));

        writeToFile(file.getPath(), writer -> write(writer, "new"));

        assertThat(readFileAsString(file)).isEqualTo("new");
    }

    @Test
    public void testWriteToFile_FailsWhenParentDirectoryDoesNotExist() {
        final File file = new File(tempFolder.getRoot(), "no-such-dir" + File.separator + "out.txt");

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> writeToFile(file.getPath(), writer -> write(writer, "content")))
            .withMessageContaining("Cannot write to file");
    }

    /**
     * Documents the current behaviour: when the write handler fails, the exception propagates as is and
     * the (already created and truncated) file is left behind without the buffered content being flushed.
     * No production behaviour was changed.
     */
    @Test
    public void testWriteToFile_HandlerFailureLeavesAnEmptyFileBehind() throws IOException {
        final File file = new File(tempFolder.getRoot(), "handler-failure.txt");

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> writeToFile(file.getPath(), writer -> {
                write(writer, "some content");
                throw new IllegalStateException("handler failed");
            }));

        assertThat(file).exists();
        assertThat(readFileAsString(file)).isEmpty();
    }

    @Test
    public void testReadFileAsString() throws IOException {
        final File file = tempFolder.newFile("read.txt");
        Files.write(file.toPath(), "Ünïcødé content\n".getBytes(StandardCharsets.UTF_8));

        assertThat(readFileAsString(file)).isEqualTo("Ünïcødé content\n");
    }

    @Test
    public void testReadFileAsString_EmptyFile() throws IOException {
        assertThat(readFileAsString(tempFolder.newFile("empty.txt"))).isEmpty();
    }

    @Test
    public void testReadFileAsString_MissingFile() {
        final File file = new File(tempFolder.getRoot(), "missing.txt");

        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> readFileAsString(file))
            .withMessageContaining("Unable to read file");
    }

    @Test
    public void testReadResourceAsString() {
        assertThat(readResourceAsString(TEST_RESOURCE))
            .isEqualTo("Hello Jarviz! Ünïcødé 日本語\nsecond line\n");
    }

    /**
     * Documents the current behaviour: a missing resource surfaces as a {@link NullPointerException}
     * from the underlying stream instead of the {@link IllegalArgumentException} used for missing files.
     * No production behaviour was changed.
     */
    @Test
    public void testReadResourceAsString_MissingResource() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> readResourceAsString("no-such-resource.txt"));
    }

    private static void write(final Writer writer, final String content) {
        try {
            writer.write(content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
