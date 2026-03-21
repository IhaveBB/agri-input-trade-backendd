package org.example.springboot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.springboot.annotation.RequiresRole;
import org.example.springboot.common.Result;
import org.example.springboot.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件上传控制器
 * 提供文件上传功能
 *
 * @author IhaveBB
 * @date 2026/03/19
 */
@Tag(name = "文件上传接口类")
@RequestMapping("/file")
@RestController
public class FileController {

    @Autowired
    private FileService fileService;
    private static final Logger LOGGER = LoggerFactory.getLogger(FileController.class);

    /**
     * 文件上传
     * 权限：需要登录
     *
     * @param file 文件
     * @return 上传结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "文件上传")
    @RequiresRole
    @PostMapping("/upload/img")
    public Result<?> upLoad(@RequestParam("file") MultipartFile file) {
        return Result.success(fileService.upLoad(file, "img"));
    }

    /**
     * 多文件上传
     * 权限：需要登录
     *
     * @param files 文件列表
     * @return 上传结果
     * @author IhaveBB
     * @date 2026/03/19
     */
    @Operation(summary = "多文件上传，并且在有失败时删除已上传成功的文件")
    @RequiresRole
    @PostMapping("/uploadMultiple")
    public Result<?> uploadMultiple(@RequestParam("files") List<MultipartFile> files) {
        return Result.success(fileService.uploadMultiple(files));
    }
}





