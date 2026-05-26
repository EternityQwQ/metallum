package net.irisshaders.iris.vertices;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.VertexFormat;

public interface ExtendingBufferBuilder {
	void iris$beginWithoutExtending(PrimitiveTopology drawMode, VertexFormat vertexFormat);
}
