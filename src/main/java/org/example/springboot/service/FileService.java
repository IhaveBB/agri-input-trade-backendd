package org.example.springboot.service;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileService.class);

    public String upLoad(MultipartFile file, String fileType) {
        if (StringUtils.isBlank(file.getOriginalFilename())) {
            LOGGER.error("文件不存在");
            throw new BusinessException(ErrorCodeEnum.FILE_NOT_FOUND, "文件不存在！");
        }
        LOGGER.info("upload FILE:" + file.getOriginalFilename());
        String path = FileUtil.saveFile(file, null, fileType);
        if (StringUtils.isNotBlank(path)) {
            return path;
        } else {
            throw new BusinessException(ErrorCodeEnum.FILE_UPLOAD_FAILED, "文件上传失败");
        }
    }

    public void fileRemove(String filename) {
        String filePath = "\\img\\" + filename;
        boolean res = FileUtil.deleteFile(filePath);
        if (!res) {
            throw new BusinessException(ErrorCodeEnum.FILE_OPERATION_FAILED, "删除失败！");
        }
    }

    public List<String> uploadMultiple(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            LOGGER.error("没有文件上传");
            throw new BusinessException(ErrorCodeEnum.PARAM_ERROR, "没有文件上传");
        }

        List<String> successPaths = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        for (MultipartFile file : files) {
            if (StringUtils.isEmpty(file.getOriginalFilename())) {
                failedFiles.add(file.getOriginalFilename() + ": 文件不存在");
                continue;
            }
            LOGGER.info("upload FILE:" + file.getOriginalFilename());
            String path = FileUtil.saveFile(file, null, "common");
            if (StringUtils.isNotBlank(path)) {
                successPaths.add(path);
            } else {
                failedFiles.add(file.getOriginalFilename() + ": 文件上传失败");
            }
        }

        // 检查是否所有文件都成功上传
        if (!failedFiles.isEmpty()) {
            // 如果有文件上传失败，删除所有成功上传的文件
            for (String path : successPaths) {
                File uploadedFile = new File(path);
                if (uploadedFile.exists() && uploadedFile.isFile()) {
                    if (uploadedFile.delete()) {
                        LOGGER.info("Deleted successfully uploaded file: " + path);
                    } else {
                        LOGGER.warn("Failed to delete file: " + path);
                    }
                }
            }
            throw new BusinessException(ErrorCodeEnum.FILE_UPLOAD_FAILED, "部分文件上传失败: " + failedFiles);
        } else {
            // 如果全部成功，则只返回成功路径
            return successPaths;
        }
    }
}
