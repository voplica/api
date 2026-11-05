# Changelog

## 1.5.0 (2026-11-05)

buf breaking against 1.4.0: none.

- Added messages: `voplica.types.ai.common.AudioWindow`.
- Added enums: `voplica.types.ai.common.MediaReferenceRole`.
- Added fields: `voplica.types.ai.common.AudioWindow.end_ms` (2), `voplica.types.ai.common.AudioWindow.start_ms` (1).
- Added enum values: `voplica.types.ai.common.MediaReferenceRole.AUDIO_REFERENCE` (52), `voplica.types.ai.common.MediaReferenceRole.CANNY_VIDEO` (32), `voplica.types.ai.common.MediaReferenceRole.DEPTH_VIDEO` (31), `voplica.types.ai.common.MediaReferenceRole.DRIVING_AUDIO` (50), `voplica.types.ai.common.MediaReferenceRole.EFFECT_VIDEO` (44), `voplica.types.ai.common.MediaReferenceRole.END_FRAME` (2), `voplica.types.ai.common.MediaReferenceRole.END_VIDEO` (42), `voplica.types.ai.common.MediaReferenceRole.FRAME_AT` (3), `voplica.types.ai.common.MediaReferenceRole.IDENTITY_IMAGES` (11), `voplica.types.ai.common.MediaReferenceRole.IDENTITY_SHEET` (10), `voplica.types.ai.common.MediaReferenceRole.MEDIA_REFERENCE_ROLE_UNSPECIFIED` (0), `voplica.types.ai.common.MediaReferenceRole.POSE_VIDEO` (30), `voplica.types.ai.common.MediaReferenceRole.SOURCE_VIDEO` (40), `voplica.types.ai.common.MediaReferenceRole.START_FRAME` (1), `voplica.types.ai.common.MediaReferenceRole.START_VIDEO` (41), `voplica.types.ai.common.MediaReferenceRole.STYLE_IMAGE` (20), `voplica.types.ai.common.MediaReferenceRole.STYLE_VIDEO` (43), `voplica.types.ai.common.MediaReferenceRole.VOICE_REFERENCE` (51).

## 1.4.0 (2026-11-05)

buf breaking against 1.0.1: none.

- Added messages: `voplica.types.file.SafePreviewSelection`.
- Added fields: `voplica.api.ai.dataset.UpdateDatasetRequest.safe_default_preview` (10), `voplica.api.ai.model.CreateModelVariantRequest.training_allowed` (9), `voplica.api.ai.model.UpdateModelVariantRequest.safe_default_preview` (13), `voplica.api.ai.model.UpdateModelVariantRequest.training_allowed` (14), `voplica.api.ai.training.CreateModelVariantFromTrainingCheckpointRequest.training_allowed` (8), `voplica.api.character.UpdateCharacterRequest.safe_default_preview` (13), `voplica.api.workflow.UpdateWorkflowVariantRequest.safe_default_preview` (11), `voplica.types.ai.dataset.Dataset.safe_default_preview` (15), `voplica.types.ai.model.ImportVariantFields.training_allowed` (5), `voplica.types.ai.model.ModelVariant.safe_default_preview` (24), `voplica.types.ai.model.ModelVariant.training_allowed` (25), `voplica.types.ai.model.ModelVariantShortPreview.safe_default_preview` (6), `voplica.types.character.Character.safe_default_preview` (13), `voplica.types.file.FileVariantFilter.url_lifetime_seconds` (5), `voplica.types.file.SafePreviewSelection.clear` (2), `voplica.types.file.SafePreviewSelection.use_asset_id` (1), `voplica.types.workflow.WorkflowVariant.safe_default_preview` (19), `voplica.types.workflow.WorkflowVariantShortPreview.safe_default_preview` (3).
- Added enum values: `voplica.types.ai.common.ModelFamily.NANO_BANANA_2_1` (26).

## 1.0.1 (2026-10-26)

buf breaking against 1.0.0: none.

- Documentation only.

## 1.0.0 (2026-10-25)

The initial public release of the Voplica API: 211 RPCs in 20 services.
