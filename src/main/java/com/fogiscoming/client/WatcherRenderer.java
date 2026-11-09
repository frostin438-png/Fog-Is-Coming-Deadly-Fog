package com.fogiscoming.client;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.entity.WatcherEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** A tall, thin, pitch-black figure with two faint pale eyes. Casts no shadow. */
@OnlyIn(Dist.CLIENT)
public final class WatcherRenderer extends MobRenderer<WatcherEntity, HumanoidModel<WatcherEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(FogIsComing.id("watcher"), "main");
    private static final ResourceLocation TEXTURE = FogIsComing.id("textures/entity/watcher.png");
    private static final RenderType EYES = RenderType.eyes(FogIsComing.id("textures/entity/watcher_eyes.png"));

    public WatcherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(LAYER)), 0.0F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return EYES;
            }
        });
    }

    public static LayerDefinition createLayer() {
        return LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F), 64, 64);
    }

    @Override
    protected void scale(WatcherEntity entity, PoseStack pose, float partialTick) {
        // Too tall and too thin to be a person.
        pose.scale(0.78F, 1.2F, 0.78F);
    }

    @Override
    public ResourceLocation getTextureLocation(WatcherEntity entity) {
        return TEXTURE;
    }
}
