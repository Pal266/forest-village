package forestsettlement.render;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;

public class Mesh {

    private static final int FLOATS_PER_VERTEX = 6; // position(3) + color(3)
    private static final int STRIDE = FLOATS_PER_VERTEX * Float.BYTES;
    private static final long COLOR_OFFSET = 3L * Float.BYTES;

    private final int vao;
    private final int vbo;
    private final int ebo;
    private final int indexCount;

    public static Mesh quad() {
        float[] vertices = {
                // x,     y,     z,    r,  g,  b
                -0.8f, -0.3f, 0.0f,  1f, 0f, 0f,
                -0.2f, -0.3f, 0.0f,  0f, 1f, 0f,
                -0.2f,  0.3f, 0.0f,  0f, 0f, 1f,
                -0.8f,  0.3f, 0.0f,  1f, 1f, 0f,
        };

        int[] indices = {
                0, 1, 2,
                2, 3, 0,
        };

        return new Mesh(vertices, indices);
    }

    public static Mesh cube() {
        float[] vertices = {
                // Front (red)      x     y     z    r   g   b
                0.2f, -0.3f,  0.3f, 1f, 0f, 0f,
                0.8f, -0.3f,  0.3f, 1f, 0f, 0f,
                0.8f,  0.3f,  0.3f, 1f, 0f, 0f,
                0.2f,  0.3f,  0.3f, 1f, 0f, 0f,
                // Back (green)
                0.8f, -0.3f, -0.3f, 0f, 1f, 0f,
                0.2f, -0.3f, -0.3f, 0f, 1f, 0f,
                0.2f,  0.3f, -0.3f, 0f, 1f, 0f,
                0.8f,  0.3f, -0.3f, 0f, 1f, 0f,
                // Left (blue)
                0.2f, -0.3f, -0.3f, 0f, 0f, 1f,
                0.2f, -0.3f,  0.3f, 0f, 0f, 1f,
                0.2f,  0.3f,  0.3f, 0f, 0f, 1f,
                0.2f,  0.3f, -0.3f, 0f, 0f, 1f,
                // Right (yellow)
                0.8f, -0.3f,  0.3f, 1f, 1f, 0f,
                0.8f, -0.3f, -0.3f, 1f, 1f, 0f,
                0.8f,  0.3f, -0.3f, 1f, 1f, 0f,
                0.8f,  0.3f,  0.3f, 1f, 1f, 0f,
                // Top (cyan)
                0.2f,  0.3f,  0.3f, 0f, 1f, 1f,
                0.8f,  0.3f,  0.3f, 0f, 1f, 1f,
                0.8f,  0.3f, -0.3f, 0f, 1f, 1f,
                0.2f,  0.3f, -0.3f, 0f, 1f, 1f,
                // Bottom (magenta)
                0.2f, -0.3f, -0.3f, 1f, 0f, 1f,
                0.8f, -0.3f, -0.3f, 1f, 0f, 1f,
                0.8f, -0.3f,  0.3f, 1f, 0f, 1f,
                0.2f, -0.3f,  0.3f, 1f, 0f, 1f,
        };

        int[] indices = new int[36];
        for (int face = 0; face < 6; face++) {
            int vertexOffset = face * 4;
            int indexOffset = face * 6;

            indices[indexOffset]     = vertexOffset;
            indices[indexOffset + 1] = vertexOffset + 1;
            indices[indexOffset + 2] = vertexOffset + 2;
            indices[indexOffset + 3] = vertexOffset + 2;
            indices[indexOffset + 4] = vertexOffset + 3;
            indices[indexOffset + 5] = vertexOffset;
        }

        return new Mesh(vertices, indices);
    }

    public Mesh(float[] interleavedVertices, int[] indices) {
        this.indexCount = indices.length;

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, interleavedVertices, GL_STATIC_DRAW);

        ebo = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);

        glVertexAttribPointer(0, 3, GL_FLOAT, false, STRIDE, 0);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 3, GL_FLOAT, false, STRIDE, COLOR_OFFSET);
        glEnableVertexAttribArray(1);

        glBindVertexArray(0);
    }

    public void draw() {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void destroy() {
        glDeleteBuffers(vbo);
        glDeleteBuffers(ebo);
        glDeleteVertexArrays(vao);
    }
}
