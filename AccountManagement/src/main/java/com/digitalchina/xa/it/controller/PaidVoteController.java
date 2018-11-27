package com.digitalchina.xa.it.controller;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.digitalchina.xa.it.model.PaidVoteDetailDomain;
import com.digitalchina.xa.it.service.EthAccountService;
import com.digitalchina.xa.it.service.PaidVoteDetailService;
import com.digitalchina.xa.it.service.PaidVoteTop10Service;
import com.digitalchina.xa.it.service.PaidVoteTopicService;
import com.digitalchina.xa.it.util.DecryptAndDecodeUtils;
import com.digitalchina.xa.it.util.Encrypt;
import com.digitalchina.xa.it.util.EncryptImpl;

@Controller
@RequestMapping(value = "/paidVotes")
public class PaidVoteController {
	@Autowired
	private PaidVoteDetailService paidVoteDetailService;
	@Autowired
	private PaidVoteTop10Service paidVoteTop10Service;
	@Autowired
	private PaidVoteTopicService paidVoteTopicService;
	@Autowired
	private EthAccountService ethAccountService;
	
	@ResponseBody
	@GetMapping("/insertVoteDetail")
	public Object voteToSomebody(
	        @RequestParam(name = "param", required = true) String jsonValue){
		Map<String, Object> modelMap = DecryptAndDecodeUtils.decryptAndDecode(jsonValue);
		if(!(boolean) modelMap.get("success")){
			return modelMap;
		}
		JSONObject jsonObj = JSONObject.parseObject((String) modelMap.get("data"));
    	String toaccount = jsonObj.getString("toaccount");
    	String fromaccount = jsonObj.getString("fromaccount");
    	String toitcode = jsonObj.getString("toitcode");
    	String fromitcode = jsonObj.getString("fromitcode");
    	String turncount = jsonObj.getString("turncount");
    	String remark = jsonObj.getString("remark");
    	Integer topicId = jsonObj.getInteger("topicId");
		
    	String returnStr = paidVoteDetailService.voteToSomebody(toaccount, fromaccount, toitcode, fromitcode, turncount, remark, topicId);
//		if( res <= 5 && res >=0) {
//			modelMap.put("success", true);
//		} else {
//			modelMap.put("success", false);
//			modelMap.put("errMsg", "skippingReading");
//		}
    	
    	if(returnStr == "notEnough") {
    		modelMap.put("success", false);
    		modelMap.put("errMsg", "notEnough");
    	}
		
		return modelMap;
	}
	
	@ResponseBody
	@GetMapping("/top5")
	public String getTop5(@RequestParam(name = "topicId", required = true) Integer topicId) {
		List<Map<String, Object>> dataList = paidVoteDetailService.selectTop10(topicId);
		List<Map<String, Object>> returnList = new ArrayList<>();
		for(int i = 0; i < dataList.size(); i++) {
			Map<String, Object> map = new HashMap<>();
			Integer value = ((BigDecimal) dataList.get(i).get("balance")).intValue();
			//new BigDecimal((Double)dataList.get(i).get("balance"))
			
			map.put("key", dataList.get(i).get("itcode"));
			map.put("value", value);
			returnList.add(map);
		}
		String data = JSON.toJSONString(returnList);
		return data;
	}
	
	@ResponseBody
	@GetMapping("/getDetail")
	public Map<String, Object> getDetail(@RequestParam(name = "itcode", required = true) String itcode, 
			@RequestParam(name = "topicId", required = true) String topicId) {
		Map<String, Object> modelMap = new HashMap<String, Object>();
		List<Map<String, Object>> returnList = new ArrayList<>();
		List<PaidVoteDetailDomain> dataList = paidVoteDetailService.selectPaidVoteDetailByBeVotedItcode(itcode, Integer.valueOf(topicId));
		if(dataList.isEmpty()) {
			modelMap.put("success", "dataNull");
			return modelMap;
		}
		for(int i = 0; i < dataList.size(); i++) {
			Map<String, Object> map = new HashMap<>();
			String voteAddress = dataList.get(i).getVoteAddress();
			int numberOfVotes = dataList.get(i).getNumberOfVotes();
			String voteItcode = ethAccountService.selectEthAccountByAddress(voteAddress).getItcode();
			map.put("voteItcode", voteItcode);
			map.put("voteAddress", voteAddress);
			map.put("beVotedItcode", dataList.get(i).getBeVotedItcode());
			map.put("beVotedAdress", dataList.get(i).getBeVotedAddress());
			map.put("numberOfVotes", String.valueOf(numberOfVotes));
			map.put("transactionHash", dataList.get(i).getTransactionHash());
			returnList.add(map);
		}
		modelMap.put("success", JSONObject.toJSON(returnList));
		
		return modelMap;
	}
}
