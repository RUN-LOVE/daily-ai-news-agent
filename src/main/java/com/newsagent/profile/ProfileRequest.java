package com.newsagent.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProfileRequest(
        @NotBlank(message = "称呼不能为空")
        @Size(max = 50, message = "称呼最多 50 个字符")
        String displayName,

        @Size(max = 100, message = "身份最多 100 个字符")
        String role,

        @Email(message = "邮箱格式不正确")
        @Size(max = 200, message = "邮箱最多 200 个字符")
        String email,

        @Size(max = 20, message = "最多选择 20 个话题")
        List<@Size(max = 50, message = "单个话题最多 50 个字符") String> topics,

        @Size(max = 30, message = "最多填写 30 个关键词")
        List<@Size(max = 50, message = "单个关键词最多 50 个字符") String> keywords,

        @Size(max = 30, message = "最多填写 30 个排除词")
        List<@Size(max = 50, message = "单个排除词最多 50 个字符") String> excludeKeywords,

        @Size(max = 20, message = "最多选择 20 个新闻源")
        List<String> sources,

        @Pattern(regexp = "zh|en", message = "语言只能是 zh 或 en")
        String language,

        @Min(value = 1, message = "每日条数至少为 1")
        @Max(value = 20, message = "每日条数最多为 20")
        Integer maxItems,

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "推送时间格式应为 HH:mm")
        String deliverTime,

        @Pattern(regexp = "INBOX|EMAIL", message = "推送方式只能是 INBOX 或 EMAIL")
        String pushChannel
) {
}
