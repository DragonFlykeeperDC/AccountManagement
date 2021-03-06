package com.digitalchina.xa.it.job;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.admin.Admin;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import com.digitalchina.xa.it.dao.EthAccountDAO;
import com.digitalchina.xa.it.dao.PaidVoteDetailDAO;
import com.digitalchina.xa.it.dao.WalletAccountDAO;
import com.digitalchina.xa.it.dao.WalletTransactionDAO;
import com.digitalchina.xa.it.model.WalletTransactionDomain;
import com.digitalchina.xa.it.service.WalletTransactionService;
import com.digitalchina.xa.it.util.HttpRequest;

import rx.Subscription;
import scala.util.Random;

@Component
public class TimedTask {
	@Autowired
    private WalletAccountDAO walletAccountDAO;
	@Autowired
	private WalletTransactionDAO walletTransactionDAO;
	
	@Autowired
	private PaidVoteDetailDAO paidVoteDetailDAO;
	
	private static String[] ip = {"http://10.7.10.124:8545","http://10.7.10.125:8545","http://10.0.5.217:8545","http://10.0.5.218:8545","http://10.0.5.219:8545"};

	@Transactional
	@Scheduled(cron="10,40 * * * * ?")
	public void updateVoteTopic(){
		Web3j web3j = Web3j.build(new HttpService(ip[new Random().nextInt(5)]));
		List<WalletTransactionDomain> wtdList = walletTransactionDAO.selectHashAndAccounts();
		if(wtdList == null) {
			web3j.shutdown();
			return;
		}
		try {
			for(int i = 0; i < wtdList.size(); i++) {
				String transactionHash = wtdList.get(i).getTransactionHash();
				System.out.println("定时任务_链上未确认_transactionHash:" + transactionHash);
				TransactionReceipt tr = web3j.ethGetTransactionReceipt(transactionHash).sendAsync().get().getResult();
				if(tr == null) {
					System.out.println(transactionHash + "仍未确认，查询下一个未确认交易");
					continue;
				}
				if(!tr.getBlockHash().contains("00000000")) {
					String accountFrom = wtdList.get(i).getAccountFrom();
					String accountTo = wtdList.get(i).getAccountTo();
					
					BigInteger balanceFrom = web3j.ethGetBalance(accountFrom,DefaultBlockParameterName.LATEST).send().getBalance();
					BigInteger balanceTo = web3j.ethGetBalance(accountTo,DefaultBlockParameterName.LATEST).send().getBalance();
					
					BigInteger gasUsed = tr.getGasUsed();
					BigInteger blockNumber = tr.getBlockNumber();
					WalletTransactionDomain wtd = new WalletTransactionDomain();
					wtd.setTransactionHash(transactionHash);
					wtd.setBalanceFrom(Double.parseDouble(balanceFrom.toString()));
					wtd.setBalanceTo(Double.parseDouble(balanceTo.toString()));
					wtd.setGas(Double.valueOf(gasUsed.toString()));
					wtd.setConfirmBlock(Integer.valueOf(blockNumber.toString()));
					wtd.setStatus(1);
					walletTransactionDAO.updateByTransactionHash(wtd);
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			web3j.shutdown();
		}
	}
	
	//每天8点30分30秒执行前一天考勤奖励
	@Transactional
	@Scheduled(cron="30 30 08 * * ?")
//	@Scheduled(cron="55 30 14 * * ?")
	public void sendAttendanceRewards(){
		System.err.println("开始考勤奖励员工编号获取...");
		String result = "";
		List<String> resultList = walletAccountDAO.selectUserNoAfter21();
		resultList.addAll(walletAccountDAO.selectUserNoBefore8());
		
		for (String string : resultList) {
			result += (string + ",");
		}
		if(result == ""){
			return;
		}
		result = result.substring(0, result.length() - 1 );
		System.err.println(result);
		//FIXME 发送get请求，http://10.0.5.217:8080/eth/attendanceReward/result
		HttpRequest.sendGet("http://10.0.5.217:8080/eth/attendanceReward/" + result, "");
	}
	

	@Scheduled(fixedRate=15000)
	public void updateTurnResultStatusJob(){
		Integer index = (int)(Math.random()*5);
    	String ipAddress = ip[index];
		System.err.println("更新交易状态时以太坊链接的ip为"+ipAddress);
		
		Admin admin = Admin.build(new HttpService(ipAddress));
		
		List<String> list1 = paidVoteDetailDAO.selectUnfinishedTransaction();
		if(list1.size() > 0){
			for (String transactionHash : list1) {
				if(transactionHash == ""){
					continue;
				}
				EthTransaction ethTransaction = null;
				try {
					ethTransaction = admin.ethGetTransactionByHash(transactionHash).sendAsync().get();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				org.web3j.protocol.core.methods.response.Transaction result = ethTransaction.getResult();
				if(result == null){
					System.err.println(transactionHash);
					continue;
				}
		    	String blockHash = result.getBlockHash();
		    	System.err.println("blockHash为"+blockHash);
		    	//更新状态
		    	if(!blockHash.contains("0000000000000000000")){
		    		paidVoteDetailDAO.updateTransactionStatus(transactionHash);
		    	}
			}
		}
	}

}
