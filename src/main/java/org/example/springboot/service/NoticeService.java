package org.example.springboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.springboot.entity.Notice;
import org.example.springboot.enums.ErrorCodeEnum;
import org.example.springboot.exception.BusinessException;
import org.example.springboot.mapper.NoticeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoticeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoticeService.class);

    @Autowired
    private NoticeMapper noticeMapper;

    public List<Notice> getAll() {
        QueryWrapper<Notice> queryWrapper = new QueryWrapper<>();
        return noticeMapper.selectList(queryWrapper);
    }

    public List<Notice> getWithLimit(Integer count) {
        LOGGER.info("limit:" + count);
        QueryWrapper<Notice> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("time");
        Page<Notice> page = new Page<>(1, count);
        IPage<Notice> resultPage = noticeMapper.selectPage(page, queryWrapper);

        List<Notice> notices = resultPage.getRecords();
        LOGGER.info("notices:" + notices);
        return notices;
    }

    public Page<Notice> getNoticesByPage(String title, Integer currentPage, Integer size) {
        LOGGER.info("title:" + title + " cP" + currentPage + " size" + size);
        LambdaQueryWrapper<Notice> wrappers = Wrappers.lambdaQuery();
        if (StringUtils.isNotBlank(title)) {
            LOGGER.info("isNotBlank");
            wrappers.like(Notice::getTitle, title);
        }

        return noticeMapper.selectPage(new Page<>(currentPage, size), wrappers);
    }

    public Notice getById(int id) {
        Notice notice = noticeMapper.selectById(id);
        LOGGER.info("notices:" + notice);
        return notice;
    }

    public void add(Notice notice) {
        int res = noticeMapper.insert(notice);
        LOGGER.info("NEW notice:" + notice);
        if (res <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "新增失败");
        }
    }

    public void update(int id, Notice notice) {
        notice.setId(id);
        LOGGER.info("UPDATE notice:" + notice);
        int res = noticeMapper.updateById(notice);
        if (res <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "修改失败");
        }
    }

    public void deleteBatch(List<Integer> ids) {
        LOGGER.info("DELETEBATCH notices IDS:" + ids);
        int res = noticeMapper.deleteBatchIds(ids);
        if (res <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "删除失败");
        }
    }

    public void deleteById(int id) {
        LOGGER.info("DELETE notices ID:" + id);
        int res = noticeMapper.deleteById(id);
        if (res <= 0) {
            throw new BusinessException(ErrorCodeEnum.OPERATION_FAILED, "删除失败");
        }
    }
}
