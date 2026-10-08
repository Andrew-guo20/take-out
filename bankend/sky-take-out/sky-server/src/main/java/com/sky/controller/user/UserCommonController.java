package com.sky.controller.user;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 用户端通用接口
 */
@RestController
@RequestMapping("/user/common")
@Slf4j
@Api(tags = "用户端通用接口")
public class UserCommonController {

    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 上传头像
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("上传头像")
    public Result<String> upload(MultipartFile file){
        log.info("用户头像上传");

        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !originalFilename.contains(".")) {
                return Result.error(MessageConstant.UPLOAD_FAILED);
            }
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String objectName = "avatar/" + UUID.randomUUID() + extension;

            String filePath = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(filePath);
        } catch (IOException e) {
            log.error("头像上传失败：{}", e);
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}
