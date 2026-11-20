# Changelog

## 1.13.0 (2026-11-20)

buf breaking against 1.5.0: none.

- Added RPCs: `voplica.api.account.AccountService/SetOwnGenerationFilter`, `voplica.api.ai.model.ModelVariantService/ListPromptProfiles`.
- Added messages: `voplica.api.account.SetOwnGenerationFilterRequest`, `voplica.api.account.SetOwnGenerationFilterResponse`, `voplica.api.ai.model.ListPromptProfilesRequest`, `voplica.api.ai.model.ListPromptProfilesResponse`, `voplica.types.ai.model.PromptProfile`.
- Added enums: `voplica.types.account.GenerationFilter`, `voplica.types.ai.common.InferenceStrategy`, `voplica.types.ai.common.LoraUsageStrategy`.
- Added fields: `voplica.api.account.SetOwnGenerationFilterRequest.generation_filter` (1), `voplica.api.account.SetOwnGenerationFilterResponse.changed` (2), `voplica.api.account.SetOwnGenerationFilterResponse.generation_filter` (1), `voplica.api.ai.model.CreateModelVariantRequest.inference_strategy` (11), `voplica.api.ai.model.CreateModelVariantRequest.lora_usage_strategy` (13), `voplica.api.ai.model.CreateModelVariantRequest.nsfw_prompt_profile` (14), `voplica.api.ai.model.CreateModelVariantRequest.prompt_profile` (12), `voplica.api.ai.model.CreateModelVariantRequest.sampling_regime` (10), `voplica.api.ai.model.ListPromptProfilesRequest.model_family` (1), `voplica.api.ai.model.ListPromptProfilesResponse.profiles` (1), `voplica.api.ai.model.UpdateModelVariantRequest.inference_strategy` (15), `voplica.api.ai.model.UpdateModelVariantRequest.lora_usage_strategy` (17), `voplica.api.ai.model.UpdateModelVariantRequest.nsfw_prompt_profile` (18), `voplica.api.ai.model.UpdateModelVariantRequest.prompt_profile` (16), `voplica.api.creator.GetMyCreatorProgramResponse.links` (4), `voplica.types.account.VoplicaAccount.generation_filter` (10), `voplica.types.ai.common.ModelFamilyExtension.additive_tokenizer` (16), `voplica.types.ai.common.ModelFamilyExtension.inference_strategies` (15), `voplica.types.ai.common.ModelFamilyExtension.lora_usage_strategies` (17), `voplica.types.ai.model.ImportVersionSelection.inference_strategy` (7), `voplica.types.ai.model.ImportVersionSelection.lora_usage_strategy` (9), `voplica.types.ai.model.ImportVersionSelection.nsfw_prompt_profile` (10), `voplica.types.ai.model.ImportVersionSelection.prompt_profile` (8), `voplica.types.ai.model.ImportVersionSelection.sampling_regime` (6), `voplica.types.ai.model.ModelVariant.inference_strategy` (26), `voplica.types.ai.model.ModelVariant.lora_usage_strategy` (28), `voplica.types.ai.model.ModelVariant.nsfw_prompt_profile` (29), `voplica.types.ai.model.ModelVariant.prompt_profile` (27), `voplica.types.ai.model.PromptProfile.description` (3), `voplica.types.ai.model.PromptProfile.display_name` (2), `voplica.types.ai.model.PromptProfile.family_default` (4), `voplica.types.ai.model.PromptProfile.has_nsfw_instructions` (5), `voplica.types.ai.model.PromptProfile.key` (1), `voplica.types.content.Content.external_music_flagged` (19).
- Added enum values: `voplica.types.account.GenerationFilter.GENERATION_FILTER_DEFAULT` (1), `voplica.types.account.GenerationFilter.GENERATION_FILTER_DISABLED` (3), `voplica.types.account.GenerationFilter.GENERATION_FILTER_ENABLED` (2), `voplica.types.account.GenerationFilter.GENERATION_FILTER_UNSPECIFIED` (0), `voplica.types.ai.common.InferenceStrategy.INFERENCE_STRATEGY_SINGLE_PASS` (1), `voplica.types.ai.common.InferenceStrategy.INFERENCE_STRATEGY_TWO_PASS` (2), `voplica.types.ai.common.InferenceStrategy.INFERENCE_STRATEGY_UNSPECIFIED` (0), `voplica.types.ai.common.LoraUsageStrategy.LORA_USAGE_STRATEGY_AS_TRAINED` (1), `voplica.types.ai.common.LoraUsageStrategy.LORA_USAGE_STRATEGY_MLP_ONLY` (2), `voplica.types.ai.common.LoraUsageStrategy.LORA_USAGE_STRATEGY_MLP_ONLY_LOW_RANK` (3), `voplica.types.ai.common.LoraUsageStrategy.LORA_USAGE_STRATEGY_UNSPECIFIED` (0), `voplica.types.ai.common.ModelFamily.MINIMAX_H3_IMAGE` (27), `voplica.types.ai.common.ModelFileRole.ROLE_TOKENIZER` (11), `voplica.types.ai.model.ModelWeightsKind.TOKENIZER_FILE` (8).
- Changed enum values: `voplica.types.ai.common.ModelFamily.MINIMAX_H3` (14): options changed.

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
