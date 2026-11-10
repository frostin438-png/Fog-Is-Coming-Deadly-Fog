package com.fogiscoming.client;

import com.fogiscoming.FogIsComing;
import com.fogiscoming.entity.LightStalkerEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A hunched ash-grey figure with arms down to its knees, two ember eyes and a dull glow in its chest.
 * The light it has just taken is carried in its hand.
 */
@OnlyIn(Dist.CLIENT)
public final class LightStalkerRenderer extends HumanoidMobRenderer<LightStalkerEntity, HumanoidModel<LightStalkerEntity>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(FogIsComing.id("light_stalker"), "main");
    private static final ResourceLocation TEXTURE = FogIsComing.id("textures/entity/light_stalker.png");
    private static final RenderType GLOW = RenderType.eyes(FogIsComing.id("textures/entity/light_stalker_glow.png"));

    public LightStalkerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(LAYER)), 0.4F);
        this.model.crouching = true; // always hunched over
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        // Thin arms that reach the knees.
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16)
                .addBox(-2.0F, -2.0F, -1.5F, 3.0F, 18.0F, 3.0F), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror()
                .addBox(-1.0F, -2.0F, -1.5F, 3.0F, 18.0F, 3.0F), PartPose.offset(5.0F, 2.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ResourceLocation getTextureLocation(LightStalkerEntity entity) {
        return TEXTURE;
    }
}
