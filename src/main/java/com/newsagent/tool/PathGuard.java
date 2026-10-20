package com.newsagent.tool;

import com.newsagent.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
public class PathGuard {

    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("^[A-Za-z]:.*");

    private final Path workspace;
    private final Path workspaceReal;

    public PathGuard(AppProperties properties) {
        Path root;
        Path real;
        try {
            root = Path.of(properties.workspace()).toAbsolutePath().normalize();
            Files.createDirectories(root);
            real = root.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("无法初始化工作区: " + properties.workspace(), exception);
        }
        this.workspace = root;
        this.workspaceReal = real;
    }

    public Path workspace() {
        return workspace;
    }

    public Path resolve(String rawPath) {
        String value = rawPath == null || rawPath.isBlank() ? "." : rawPath.trim();
        if (value.indexOf('\0') >= 0) {
            throw new ToolException("路径包含非法字符");
        }

        String normalized = value.replace('\\', '/');
        if (normalized.startsWith("/") || WINDOWS_ABSOLUTE.matcher(normalized).matches()) {
            throw new ToolException("不允许使用绝对路径: " + rawPath);
        }

        Path candidate = workspace.resolve(normalized).normalize();
        if (!candidate.startsWith(workspace)) {
            throw new ToolException("路径超出工作区: " + rawPath);
        }
        return candidate;
    }

    public Path requireFile(String rawPath) {
        Path path = resolve(rawPath);
        if (!Files.isRegularFile(path)) {
            throw new ToolException("文件不存在: " + rawPath);
        }
        ensureRealInside(path);
        return path;
    }

    public Path requireDirectory(String rawPath) {
        Path path = resolve(rawPath);
        if (!Files.isDirectory(path)) {
            throw new ToolException("目录不存在: " + rawPath);
        }
        ensureRealInside(path);
        return path;
    }

    public Path prepareForWrite(String rawPath) {
        Path path = resolve(rawPath);
        if (path.equals(workspace)) {
            throw new ToolException("不能写入工作区根目录");
        }

        Path parent = path.getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new ToolException("无法创建父目录: " + exception.getMessage());
        }
        ensureRealInside(parent);

        if (Files.isSymbolicLink(path)) {
            throw new ToolException("不允许写入符号链接");
        }
        if (Files.isDirectory(path)) {
            throw new ToolException("目标路径是目录: " + rawPath);
        }
        return path;
    }

    private void ensureRealInside(Path path) {
        try {
            Path real = path.toRealPath();
            if (!real.startsWith(workspaceReal)) {
                throw new ToolException("路径通过符号链接越过工作区");
            }
        } catch (IOException exception) {
            throw new ToolException("无法解析路径: " + path);
        }
    }
}
