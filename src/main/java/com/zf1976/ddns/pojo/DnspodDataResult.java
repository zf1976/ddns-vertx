package com.zf1976.ddns.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author mac
 * @date 2021/7/19
 */
public class DnspodDataResult {

    @JsonProperty("Response")
    private Response response;

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    @Override
    public String toString() {
        return "DnspodDataResult{" +
                "response=" + response +
                '}';
    }

    public static class RecordCountInfo {

        @JsonProperty("SubdomainCount")
        private int subDomainCount;
        @JsonProperty("TotalCount")
        private int totalCount;
        @JsonProperty("ListCount")
        private int listCount;

        public int getSubDomainCount() {
            return subDomainCount;
        }

        public void setSubDomainCount(int subDomainCount) {
            this.subDomainCount = subDomainCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(int totalCount) {
            this.totalCount = totalCount;
        }

        public int getListCount() {
            return listCount;
        }

        public void setListCount(int listCount) {
            this.listCount = listCount;
        }

        @Override
        public String toString() {
            return "RecordCountInfo{" +
                    "subDomainCount=" + subDomainCount +
                    ", totalCount=" + totalCount +
                    ", listCount=" + listCount +
                    '}';
        }
    }

    public static class RecordList {

        @JsonProperty("RecordId")
        private int recordId;
        @JsonProperty("Value")
        private String value;
        @JsonProperty("Status")
        private String status;
        @JsonProperty("UpdatedOn")
        private String updatedOn;
        @JsonProperty("Name")
        private String name;
        @JsonProperty("Line")
        private String line;
        @JsonProperty("LineId")
        private String lineId;
        @JsonProperty("Type")
        private String type;
        @JsonProperty("Weight")
        private String weight;
        @JsonProperty("MonitorStatus")
        private String monitorStatus;
        @JsonProperty("Remark")
        private String remark;
        @JsonProperty("TTL")
        private int ttl;
        @JsonProperty("MX")
        private int mx;

        public int getRecordId() {
            return recordId;
        }

        public void setRecordId(int recordId) {
            this.recordId = recordId;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getUpdatedOn() {
            return updatedOn;
        }

        public void setUpdatedOn(String updatedOn) {
            this.updatedOn = updatedOn;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLine() {
            return line;
        }

        public void setLine(String line) {
            this.line = line;
        }

        public String getLineId() {
            return lineId;
        }

        public void setLineId(String lineId) {
            this.lineId = lineId;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getWeight() {
            return weight;
        }

        public void setWeight(String weight) {
            this.weight = weight;
        }

        public String getMonitorStatus() {
            return monitorStatus;
        }

        public void setMonitorStatus(String monitorStatus) {
            this.monitorStatus = monitorStatus;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }

        public int getTtl() {
            return ttl;
        }

        public void setTtl(int ttl) {
            this.ttl = ttl;
        }

        public int getMx() {
            return mx;
        }

        public void setMx(int mx) {
            this.mx = mx;
        }

        @Override
        public String toString() {
            return "RecordList{" +
                    "recordId=" + recordId +
                    ", value='" + value + '\'' +
                    ", status='" + status + '\'' +
                    ", updatedOn='" + updatedOn + '\'' +
                    ", name='" + name + '\'' +
                    ", line='" + line + '\'' +
                    ", lineId='" + lineId + '\'' +
                    ", type='" + type + '\'' +
                    ", weight='" + weight + '\'' +
                    ", monitorStatus='" + monitorStatus + '\'' +
                    ", remark='" + remark + '\'' +
                    ", ttl=" + ttl +
                    ", mx=" + mx +
                    '}';
        }
    }

    public static class Response {

        @JsonProperty("RequestId")
        private String requestId;
        @JsonProperty("RecordCountInfo")
        private RecordCountInfo recordCountInfo;
        @JsonProperty("RecordList")
        private List<RecordList> recordList;

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public RecordCountInfo getRecordCountInfo() {
            return recordCountInfo;
        }

        public void setRecordCountInfo(RecordCountInfo recordCountInfo) {
            this.recordCountInfo = recordCountInfo;
        }

        public List<RecordList> getRecordList() {
            return recordList;
        }

        public void setRecordList(List<RecordList> recordList) {
            this.recordList = recordList;
        }

        @Override
        public String toString() {
            return "Response{" +
                    "requestId='" + requestId + '\'' +
                    ", recordCountInfo=" + recordCountInfo +
                    ", recordList=" + recordList +
                    '}';
        }
    }
}

