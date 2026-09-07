package forestsettlement.render;

import org.tinylog.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.opengl.GL20.*;

public class Shader {

    private final int programId;

    public Shader(String vertexResourcePath, String fragmentResourcePath) {
        int vertexShader = compile(GL_VERTEX_SHADER, loadSource(vertexResourcePath));
        int fragmentShader = compile(GL_FRAGMENT_SHADER, loadSource(fragmentResourcePath));

        programId = link(vertexShader, fragmentShader);

        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
    }

    public void use() {
        glUseProgram(programId);
    }

    public void destroy() {
        glDeleteProgram(programId);
    }

    private static String loadSource(String resourcePath) {
        try (InputStream in = Shader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Shader source not found on classpath: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read shader source: " + resourcePath, e);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            String stageName = type == GL_VERTEX_SHADER ? "vertex" : "fragment";
            Logger.error("Shader compilation failed ({} stage):\n{}", stageName, log);
            glDeleteShader(shader);
            throw new IllegalStateException("Shader compilation failed — see the log above for details.");
        }

        return shader;
    }

    private static int link(int vertexShader, int fragmentShader) {
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);

        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            Logger.error("Shader program linking failed:\n{}", log);
            glDeleteProgram(program);
            throw new IllegalStateException("Shader program linking failed — see the log above for details.");
        }

        glDetachShader(program, vertexShader);
        glDetachShader(program, fragmentShader);

        return program;
    }
}
