# Planar reflection renderer

The mirror is a second camera into the existing client world. It is not an item preview, a screen-space reflection or a periodically updated static picture. It does not move the player, load another dimension or tick the world again.

`PlanarReflection` owns the camera math independently of the mirror model. Given a plane, current eye position, view and projection, it computes the reflected eye, handedness-reversing view, clipped projection and a depth reconstruction transform. The world is clipped at the pane so blocks behind the physical mirror cannot cover the reflected scene. Near and far objects retain their different parallax and apparent scale.

`MirrorRenderer` captures color **and depth**. Every selected visible mirror renders with the current frame's camera; stale captures are never drawn. The composite tests the physical aperture against a separate copy of world depth, then reconstructs the reflected objects' virtual depth behind the pane. Copying world depth avoids reading from a framebuffer attachment while writing to it. Mirror-count and distance limits bound the additional work. Mirrors beyond the limit, and mirrors seen inside another reflection, remain transparent.

Culling and rasterization use separate projections. The secondary view reuses vanilla's unclipped culling projection, while the oblique mirror plane only clips rendered geometry. The main camera's original frustum object is restored in `finally`, not reconstructed from the draw projection. In alpha.33, that reconstruction could trap Forge 1.21.1 in `Frustum.offsetToFullyIncludeCameraCube` on the first world frame, even with reflections off. Non-finite startup draw projections are not captured. The native client fixture checks that every main world frame retains its original frustum.

By default, Iris/Oculus captures use isolated vanilla lighting. Alpha.35 adds an experimental, opt-in shader-pack capture: each selected mirror owns a separate shader pipeline and render targets, including terrain programs on older Sodium/Oculus versions. Main-view temporal counters, captured camera state and pipeline bindings are restored before normal world rendering. Creating a secondary pipeline does not reinitialize world block IDs or trigger chunk reloads.

Shader packs often reconstruct depth using only the diagonal projection terms, so an oblique projection breaks their geometry. Shader captures keep the conventional perspective matrix and apply the mirror plane through a vertex clip distance instead. Shadow/fullscreen passes temporarily restore their own winding and disable that clip distance. Non-shader captures retain oblique clipping. Oculus's extended Embeddium vertex layout also has a binding correction for the vanilla fallback.

Client settings allow up to 32 mirrors (default 2); values above four are highlighted red. Shader reflections are off by default, with a red performance warning when enabled. Every shader-enabled mirror allocates additional GPU buffers and does additional world/shadow/postprocessing work. Unsupported pipelines fall back to vanilla mirror lighting and log the failure. This is not a guarantee for arbitrary shader packs, especially geometry/tessellation stages or screen-space effects. Recursive mirrors and OptiFine compatibility are not provided.

Regression coverage:

- Off-axis projection matches the virtual world positions of reflected points on arbitrarily tilted planes.
- Near objects show stronger lateral parallax and stronger enlargement on approach than far objects.
- Reconstructed depth agrees with the main camera's projection of virtual objects.
- The native client fixture uses a room behind the viewer, three colored objects at different distances, GPU color/depth reads, lateral camera movement, an opaque occluder, resource reload and server-synchronized mouse rotation.

The general camera/depth math can support future planar views. The orchestration still assumes a single loaded world and non-recursive mirrors; teleportation, remote chunk loading, recursive portals and arbitrary shader-pack isolation are separate work, not implicit capabilities of this renderer.

The two-pass approach and its trade-offs can be compared with the author's [Immersive Portals implementation notes](https://qouteall.fun/immptl/wiki/Implementation-Details.html). This implementation does not bundle or copy Immersive Portals code.
