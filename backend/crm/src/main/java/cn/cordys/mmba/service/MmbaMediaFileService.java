package cn.cordys.mmba.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.mmba.domain.MmbaMediaFile;
import cn.cordys.mybatis.BaseMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class MmbaMediaFileService {

    @Resource
    private BaseMapper<MmbaMediaFile> mmbaMediaFileMapper;

    public MmbaMediaFile saveOrUpdate(MmbaMediaFile record, String userId) {
        MmbaMediaFile db = findByReqId(record.getReqId());
        if (db == null) {
            init(record, userId);
            mmbaMediaFileMapper.insert(record);
            return record;
        }
        inheritBase(db, record, userId);
        mmbaMediaFileMapper.update(record);
        return record;
    }

    public MmbaMediaFile saveOrUpdateByBizKey(MmbaMediaFile record, String userId) {
        MmbaMediaFile db = findByBizKey(record.getEsId(), record.getSourceFilePath());
        if (db == null) {
            init(record, userId);
            mmbaMediaFileMapper.insert(record);
            return record;
        }
        if (StringUtils.isBlank(record.getReqId())) {
            record.setReqId(db.getReqId());
        }
        inheritBase(db, record, userId);
        mmbaMediaFileMapper.update(record);
        return record;
    }

    public MmbaMediaFile findByReqId(String reqId) {
        if (StringUtils.isBlank(reqId)) {
            return null;
        }
        MmbaMediaFile query = new MmbaMediaFile();
        query.setReqId(reqId);
        return mmbaMediaFileMapper.selectOne(query);
    }

    public MmbaMediaFile findById(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        return mmbaMediaFileMapper.selectByPrimaryKey(id);
    }

    public MmbaMediaFile findByBizKey(String esId, String sourceFilePath) {
        if (StringUtils.isAnyBlank(esId, sourceFilePath)) {
            return null;
        }
        MmbaMediaFile query = new MmbaMediaFile();
        query.setEsId(esId);
        query.setSourceFilePath(sourceFilePath);
        List<MmbaMediaFile> records = mmbaMediaFileMapper.select(query);
        if (records == null || records.isEmpty()) {
            return null;
        }
        records.sort(Comparator.comparingLong(
                (MmbaMediaFile item) -> item.getUpdateTime() == null ? 0L : item.getUpdateTime()
        ).reversed());
        return records.get(0);
    }

    public List<MmbaMediaFile> listByDownloadStatus(String downloadStatus) {
        MmbaMediaFile query = new MmbaMediaFile();
        query.setDownloadStatus(downloadStatus);
        return mmbaMediaFileMapper.select(query);
    }

    private void init(MmbaMediaFile record, String userId) {
        long now = System.currentTimeMillis();
        record.setId(IDGenerator.nextStr());
        record.setCreateTime(now);
        record.setCreateUser(userId);
        record.setUpdateTime(now);
        record.setUpdateUser(userId);
    }

    private void inheritBase(MmbaMediaFile db, MmbaMediaFile record, String userId) {
        record.setId(db.getId());
        record.setCreateTime(db.getCreateTime());
        record.setCreateUser(db.getCreateUser());
        record.setUpdateTime(System.currentTimeMillis());
        record.setUpdateUser(userId);
    }
}
