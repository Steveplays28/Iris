package net.irisshaders.iris.compat.voxy;

import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.gl.texture.DepthCopyStrategy;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.Blaze3dRenderTargetExt;
import net.irisshaders.iris.targets.DepthTexture;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20C;

import java.io.IOException;

public class VoxyCompatInternal {
	public static final VoxyCompatInternal SHADERLESS = new VoxyCompatInternal(null, false);
	static boolean voxyEnabled;
	private static int guiScale = -1;
	private final IrisRenderingPipeline pipeline;
	public boolean shouldOverrideShadow;
	public boolean shouldOverride;
	private IrisLodRenderProgram solidProgram;
	private IrisLodRenderProgram translucentProgram;
	private IrisLodRenderProgram shadowProgram;
	private GlFramebuffer voxyTerrainFramebuffer;
	private GlFramebuffer voxyWaterFramebuffer;
	private GlFramebuffer voxyShadowFramebuffer;
	private DepthTexture depthTexNoTranslucent;
	private boolean translucentDepthDirty;
	private int storedDepthTex;
	private boolean incompatible = false;
	private int cachedVersion;

	public VoxyCompatInternal(@Nullable IrisRenderingPipeline pipeline, boolean voxyShadowEnabled) {
		this.pipeline = pipeline;

		// TODO: Check if Voxy rendering is enabled as well
		if (pipeline == null) {
			return;
		}

		if (pipeline.getVoxyTerrainShader().isEmpty() && pipeline.getVoxyWaterShader().isEmpty()) {
			Iris.logger.warn("No Voxy shader found in this pack.");
			incompatible = true;
			return;
		}
		cachedVersion = ((Blaze3dRenderTargetExt) Minecraft.getInstance().getMainRenderTarget()).iris$getDepthBufferVersion();

		createDepthTex(Minecraft.getInstance().getMainRenderTarget().width, Minecraft.getInstance().getMainRenderTarget().height);
		translucentDepthDirty = true;

		ProgramSource terrain = pipeline.getVoxyTerrainShader().get();
		solidProgram = IrisLodRenderProgram.createProgram(terrain.getName(), false, false, terrain, pipeline.getCustomUniforms(), pipeline);

		if (pipeline.getVoxyWaterShader().isPresent()) {
			ProgramSource water = pipeline.getVoxyWaterShader().get();
			translucentProgram = IrisLodRenderProgram.createProgram(water.getName(), false, true, water, pipeline.getCustomUniforms(), pipeline);
			voxyWaterFramebuffer = pipeline.createVoxyFramebuffer(water, true);
		}

		if (pipeline.getVoxyShadowShader().isPresent() && voxyShadowEnabled) {
			ProgramSource shadow = pipeline.getVoxyShadowShader().get();
			shadowProgram = IrisLodRenderProgram.createProgram(shadow.getName(), true, false, shadow, pipeline.getCustomUniforms(), pipeline);
			if (pipeline.hasShadowRenderTargets()) {
				voxyShadowFramebuffer = pipeline.createVoxyFramebufferShadow(shadow);
			}
			shouldOverrideShadow = true;
		} else {
			shouldOverrideShadow = false;
		}

		voxyTerrainFramebuffer = pipeline.createVoxyFramebuffer(terrain, false);

		if (translucentProgram == null) {
			translucentProgram = solidProgram;
		}

		shouldOverride = true;
	}

	public static int getVoxyBlockRenderDistance() {
		// TODO: Get this value from Voxy's API
		return 256 * 16;
	}

	public static int getRenderDistance() {
		return getVoxyBlockRenderDistance();
	}

	public static float getFarPlane() {
		// sqrt 2 to prevent the corners from being cut off
		return (float) ((getVoxyBlockRenderDistance() + 512) * Math.sqrt(2));
	}

	public static float getNearPlane() {
		// TODO: Get this value from Voxy's API
		return 0;
	}

	public static boolean checkFrame() {
		if (guiScale == -1) {
			guiScale = Minecraft.getInstance().options.guiScale().get();
		}

		// TODO: Check if Voxy rendering is enabled as well
		if ((guiScale != Minecraft.getInstance().options.guiScale().get()) && IrisApi.getInstance().isShaderPackInUse()) {
			guiScale = Minecraft.getInstance().options.guiScale().get();
			// TODO
			voxyEnabled = true;
			try {
				Iris.reload();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		return voxyEnabled;
	}

	public boolean incompatiblePack() {
		return incompatible;
	}

	public void reconnectVoxyTextures(int depthTex) {
		if (((Blaze3dRenderTargetExt) Minecraft.getInstance().getMainRenderTarget()).iris$getDepthBufferVersion() != cachedVersion) {
			cachedVersion = ((Blaze3dRenderTargetExt) Minecraft.getInstance().getMainRenderTarget()).iris$getDepthBufferVersion();
			createDepthTex(Minecraft.getInstance().getMainRenderTarget().width, Minecraft.getInstance().getMainRenderTarget().height);
		}
		if (storedDepthTex != depthTex && voxyTerrainFramebuffer != null) {
			storedDepthTex = depthTex;
			voxyTerrainFramebuffer.addDepthAttachment(depthTex);
			if (voxyWaterFramebuffer != null) {
				voxyWaterFramebuffer.addDepthAttachment(depthTex);
			}
		}
	}

	public void createDepthTex(int width, int height) {
		if (depthTexNoTranslucent != null) {
			depthTexNoTranslucent.destroy();
			depthTexNoTranslucent = null;
		}

		translucentDepthDirty = true;

		depthTexNoTranslucent = new DepthTexture("Voxy depth tex", width, height, DepthBufferFormat.DEPTH32F);
	}

	public void clear() {
		if (solidProgram != null) {
			solidProgram.free();
			solidProgram = null;
		}
		if (translucentProgram != null) {
			translucentProgram.free();
			translucentProgram = null;
		}
		if (shadowProgram != null) {
			shadowProgram.free();
			shadowProgram = null;
		}
		shouldOverrideShadow = false;
		shouldOverride = false;
		voxyTerrainFramebuffer = null;
		voxyWaterFramebuffer = null;
		voxyShadowFramebuffer = null;
		storedDepthTex = -1;
		translucentDepthDirty = true;
	}

	public IrisLodRenderProgram getSolidShader() {
		return solidProgram;
	}

	public GlFramebuffer getSolidFB() {
		return voxyTerrainFramebuffer;
	}

	public IrisLodRenderProgram getShadowShader() {
		return shadowProgram;
	}

	public GlFramebuffer getShadowFB() {
		return voxyShadowFramebuffer;
	}

	public IrisLodRenderProgram getTranslucentShader() {
		if (translucentProgram == null) {
			return solidProgram;
		}
		return translucentProgram;
	}

	public int getStoredDepthTex() {
		return storedDepthTex;
	}

	public void copyTranslucents(int width, int height) {
		if (translucentDepthDirty) {
			translucentDepthDirty = false;
			RenderSystem.bindTexture(depthTexNoTranslucent.getTextureId());
			voxyTerrainFramebuffer.bindAsReadBuffer();
			IrisRenderSystem.copyTexImage2D(GL20C.GL_TEXTURE_2D, 0, DepthBufferFormat.DEPTH32F.getGlInternalFormat(), 0, 0, width, height, 0);
		} else {
			DepthCopyStrategy.fastest(false).copy(voxyTerrainFramebuffer, storedDepthTex, null, depthTexNoTranslucent.getTextureId(), width, height);
		}
	}

	public GlFramebuffer getTranslucentFB() {
		return voxyWaterFramebuffer;
	}

	public int getDepthTexNoTranslucent() {
		if (depthTexNoTranslucent == null) return 0;

		return depthTexNoTranslucent.getTextureId();
	}
}
