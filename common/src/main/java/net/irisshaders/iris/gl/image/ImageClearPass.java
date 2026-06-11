package net.irisshaders.iris.gl.image;

public class ImageClearPass {
	private final GlImage image;

	private ImageClearPass(GlImage image) {
		this.image = image;
	}

	public static ImageClearPass create(GlImage image) {
		return new ImageClearPass(image);
	}

	public void execute() {
		image.clear();
	}

	public void destroy() {
	}
}
