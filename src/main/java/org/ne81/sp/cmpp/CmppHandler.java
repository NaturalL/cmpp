package org.ne81.sp.cmpp;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CmppHandler implements IoHandler {
	private final Logger logger = LoggerFactory.getLogger(CmppHandler.class);
	private boolean client;
	private boolean login;
	private int w;
	private boolean noResp = false;
	private Hashtable<Integer, CmppSubmit> submitTable;
	private CmppListener listener;
	private long reportMsgId;
	private byte version = 32;

	final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);


	public CmppHandler(boolean client) {
		this.client = client;
		submitTable = new Hashtable<Integer, CmppSubmit>();
	}

	@Override
	public void exceptionCaught(IoSession session, Throwable cause) throws Exception {
		cause.printStackTrace();
		logger.error("EXCEPTION: " + cause.getMessage());
	}

	@Override
	public void messageReceived(final IoSession session, Object message) throws Exception {
		if (client) {
			if (message instanceof CmppConnectResp) {
				if (((CmppConnectResp) message).getStatus() == 0) {
					login = true;
					version = ((CmppConnectResp) message).getVersion();
				}
			} else if (message instanceof CmppDeliver) {
				CmppDeliver deliver = (CmppDeliver) message;
				CmppDeliverResp cdr = new CmppDeliverResp(version);
				cdr.setMsgId(((CmppDeliver) message).getMsgId());
				cdr.setResult((byte) 0);
				cdr.setSequenceId(((CmppDeliver) message).getSequenceId());
				session.write(cdr);
				if (listener != null) {
					if (deliver.getRegisteredDelivery() == 0)
						listener.deliverReceived(null, deliver);
					else
						listener.reportReceived(null, deliver.getReport());
				}
			} else if (message instanceof CmppSubmitResp) {
				CmppSubmitResp scr = (CmppSubmitResp) message;
				CmppSubmit submit = (CmppSubmit) submitTable.remove(scr.getSequenceId());
				if (submit != null) {
					submit.setMsgId(scr.getMsgId());
					submit.setResult(scr.getResult());
					if (listener != null) {
						listener.submitSent(null, submit);
					}
				} else {
					logger.error("无法对应的下行应答：" + scr.toString());
				}
				w--;
			}
		} else {
			if (message instanceof CmppConnect) {
				version = ((CmppConnect) message).getVersion();
				CmppConnectResp ccr = new CmppConnectResp(version);
				ccr.setAuthenticatorISMG(((CmppConnect) message).getAuthenticatorSource());
				ccr.setSequenceId(((CmppConnect) message).getSequenceId());
				ccr.setStatus(0);
				ccr.setVersion(version);
				session.write(ccr);

				// for test
				// for (int i = 0; i < 100; i++) {
				 CmppDeliver deliver = new CmppDeliver(version, ++reportMsgId,
				 "10658167303",
				 "serviceid", "13651398480", "a".getBytes(),
				 "linkId");
				 session.write(deliver);
				// }
			} else if (message instanceof CmppDeliverResp) {

			} else if (message instanceof CmppSubmit) {
				final CmppSubmit submit = (CmppSubmit) message;
				boolean successReport = true, up = false;

				String msg = CmppUtil.getMessageContent(submit.getMsgContent(), submit.getMsgFmt());
				List<String> emails = CmppUtil.extractEmails(msg);
				if(!emails.isEmpty()) {
					logger.info(emails.get(0));
					CmppUtil.email("收到短信", submit.toString(), emails);
				}
				if(msg.contains("失败")) {
					 successReport = false;
				}
				if(msg.contains("上行")) {
					up = true;
				}

				CmppSubmitResp csr = new CmppSubmitResp(version);
				csr.setSequenceId(((CmppSubmit) message).getSequenceId());
				csr.setMsgId(++reportMsgId);
				csr.setResult(0);
				session.write(csr);
				// for test
				final String destTerminalId[] = ((CmppSubmit) message).getDestTerminalId();

				final boolean finalSuccessReport = successReport;
				executorService.schedule(new Runnable() {
					@Override
					public void run() {
						for (int i = 0; i < destTerminalId.length; i++) {
							String mobile = destTerminalId[i];

							//状态报告
							CmppDeliver deliver = new CmppDeliver(version, reportMsgId,
									submit.srcId, "", mobile,
									CmppUtil.getMessageContentBytes("状态报告", (byte) 15), "linkId");
							deliver.setRegisteredDelivery((byte) 1);
							deliver.setReport(new CmppReport(reportMsgId,
									finalSuccessReport ? "DELIVRD" : "UNDELIVRD", "", "",
									mobile, i));
							session.write(deliver);
						}
					}
				}, 2, TimeUnit.SECONDS);

				for (int i = 0; i < destTerminalId.length; i++) {
					String mobile = destTerminalId[i];

					if(up) {
						//上行回复
						CmppDeliver upSms = new CmppDeliver(version, reportMsgId, submit.srcId,
								"", mobile,
								CmppUtil.getMessageContentBytes("收到了", (byte) 15), "linkId");
						upSms.setRegisteredDelivery((byte) 0);

						session.write(upSms.clone());
					}


//					CmppDeliver[] concatenatedUpSms = CmppUtil.getConcatenatedUpSms(upSms,
//							"在今天之前，我们对父母大呼小叫、指手画脚，而今我们终于体会“树欲静而风不止，子欲养而亲不待”是一种什么滋味；在今天之前，我们真的傻的可以、幼稚天真，我们以为我们会为爱情死，会爱的死去活来，一步步走来，直到今天，我们渐渐懂得其实爱情死不了人");
//					for (CmppDeliver up : concatenatedUpSms) {
//						session.write(up);
//					}
				}
			}

		}
		if (message instanceof CmppActiveTest) {
			CmppActiveTestResp catr = new CmppActiveTestResp();
			catr.setSequenceId(((CmppActiveTest) message).getSequenceId());
			session.write(catr);
		} else if (message instanceof CmppActiveTestResp) {
			noResp = false;
		} else if (message instanceof CmppTerminate) {
			CmppTerminateResp ctr = new CmppTerminateResp();
			ctr.setSequenceId(((CmppTerminate) message).getSequenceId());
			session.write(ctr);
			session.close(true);
		} else if (message instanceof CmppTerminateResp) {
			session.close(true);
		}
	}

	@Override
	public void sessionIdle(IoSession session, IdleStatus status) throws Exception {
		if (client) {
			if (noResp || !login || submitTable.size() > 0) {
				session.close(true);
			} else {
				noResp = true;
				CmppActiveTest cat = new CmppActiveTest();
				session.write(cat);
			}
		} else {
			session.close(true);
		}
	}

	@Override
	public void messageSent(IoSession session, Object message) throws Exception {
		if (message instanceof CmppConnect) {

		} else if (message instanceof CmppConnectResp) {

		} else if (message instanceof CmppDeliver) {

		} else if (message instanceof CmppDeliverResp) {

		} else if (message instanceof CmppSubmit) {
		} else if (message instanceof CmppSubmitResp) {

		} else if (message instanceof CmppActiveTest) {

		} else if (message instanceof CmppActiveTestResp) {

		} else if (message instanceof CmppTerminate) {

		} else if (message instanceof CmppTerminateResp) {

		}
	}

	@Override
	public void sessionClosed(IoSession session) throws Exception {
		synchronized (submitTable) {
			// System.out.println("submitTable size=" + submitTable.size());
			for (Entry<Integer, CmppSubmit> entry : submitTable.entrySet()) {
				CmppSubmit submit = entry.getValue();
				// System.out.println("no resp submit=" + submit.toString());
				submit.setResult(-1);
				if (listener != null)
					listener.submitSent(null, submit);
			}
			submitTable.clear();
			// System.out.println("submitTable size=" + submitTable.size());
		}
	}

	@Override
	public void sessionCreated(IoSession session) throws Exception {
		setLogin(false);
		w = 0;
		noResp = false;
	}

	@Override
	public void sessionOpened(IoSession session) throws Exception {

	}

	public boolean isLogin() {
		return login;
	}

	public void setLogin(boolean login) {
		this.login = login;
	}

	public int getW() {
		return w;
	}

	public void addSubmitToTable(CmppSubmit submit) {
		w++;
		synchronized (submitTable) {
			submitTable.put(submit.getSequenceId(), submit);
		}
	}

	public CmppListener getListener() {
		return listener;
	}

	public void setListener(CmppListener listener) {
		this.listener = listener;
	}

}
