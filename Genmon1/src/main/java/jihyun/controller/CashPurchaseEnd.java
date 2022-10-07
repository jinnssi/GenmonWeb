package jihyun.controller;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.json.simple.JSONObject;

import common.controller.AbstractController;
import common.model.CartVO;
import common.model.MemberVO;
import common.model.OrderVO;
import common.model.PurchaseVO;
import hw.model.CartDAO;
import hw.model.InterCartDAO;
import jihyun.model.InterMemberDAO;
import jihyun.model.InterOrderDAO;
import jihyun.model.InterPurchaseDAO;
import jihyun.model.MemberDAO;
import jihyun.model.OrderDAO;
import jihyun.model.PurchaseDAO;
import net.nurigo.java_sdk.api.Message;

public class CashPurchaseEnd extends AbstractController {

	@Override
	public void execute(HttpServletRequest request, HttpServletResponse response) throws Exception {
		
		String method = request.getMethod();
		
		if("post".equalsIgnoreCase(method)) {
			
			HttpSession session = request.getSession();
			
			OrderVO ovo = (OrderVO)session.getAttribute("ovo");
			InterOrderDAO odao = new OrderDAO();
			
			if(super.checkLogin(request)) { // 회원 주문 입력
				odao.insertOrderMember(ovo);
				
			} else { // 비회원 주문 입력
				odao.insertOrderGuest(ovo); 
			}
			
			List<CartVO> ordertList = (List<CartVO>) session.getAttribute("ordertList");
			String orderid = odao.findOrderid(); // 주문번호 알아오기
			int total = 0;
			
			// 상품 수량 만큼 주문상세 테이블에 추가해주기
			for(CartVO cvo : ordertList) {
				int qty = cvo.getQty();
				if(qty>1) {
					for(int i =0; i<qty ; i++) {
						odao.isertOrderDetail(cvo, orderid);
					}
				} else {
					odao.isertOrderDetail(cvo, orderid);
				}
				
				total += (cvo.getAllProdvo().getParentProvo().getPrice()*cvo.getQty())*(100-cvo.getAllProdvo().getSalePcnt())/100;
			} // end of for
			
			
			// 결제 테이블
			InterPurchaseDAO purdao = new PurchaseDAO();
			String str_usePoint = request.getParameter("usePoint");
			PurchaseVO purvo = new PurchaseVO();
			
			purvo.setFk_orderid(orderid);
			purvo.setPaymentAmount(total);
			
			// 문자 내용
			String smsContent = "";
						
			// 포인트를 사용한경우 걸러주기 (비회원은 선택란이 없으니까 당연히 null 값임)
			if(str_usePoint !=null && str_usePoint !="" && Integer.parseInt(str_usePoint)!=0) { 
				
				MemberVO loginuser = (MemberVO)session.getAttribute("loginuser");
				InterMemberDAO mdao = new MemberDAO();
				
				int usePoint = Integer.parseInt(str_usePoint);
				int point = loginuser.getPoint();
				int Coin = loginuser.getCoin();
				
				if(usePoint == (point+Coin) ) { // 포인트와 적립금 전액 사용
					purvo.setUsedCoin(Coin);
					purvo.setUsedPoint(point);
					
					mdao.updateCoin(loginuser.getUserid(), 0, 0);
					
					loginuser.setPoint(0);
					loginuser.setCoin(0);
					
				} else if(usePoint>point) { // 포인트와 적립금 둘다 사용 but 전액 사용 X
					purvo.setUsedPoint(point);
					purvo.setUsedCoin(usePoint-point);
					
					mdao.updateCoin(loginuser.getUserid(), 0, Coin-usePoint+point);
					
					loginuser.setPoint(0);
					loginuser.setCoin(Coin-usePoint+point);
					
				} else if(usePoint<= point) { // 포인트만 사용
					purvo.setUsedPoint(usePoint);
					purvo.setUsedCoin(0);
					
					mdao.updateCoin(loginuser.getUserid(), point-usePoint, Coin);
					
					loginuser.setPoint(point-usePoint);
					loginuser.setCoin(Coin);
				}
				
				smsContent = "[젠틀몬스터] \r\n 우리은행 1002-950-797783(예:김지현)으로 "+(total-usePoint)+"원 입금해주세요.";
				
				session.setAttribute("loginuser", loginuser);
				
			} else { // 포인트 사용 안했다면
				
				purvo.setUsedPoint(0);
				purvo.setUsedCoin(0);
				
				smsContent = "[젠틀몬스터] \r\n 우리은행 1002-950-797783(예:김지현)으로 "+total+"원 입금해주세요.";
			}
			
			purdao.insertPurchase(purvo); // 결제테이블에 insert
			// System.out.println("결제 테이블 : "+ n);
			
			
			int fk_purchaseid = purdao.findPurchaseNum(orderid); // 결제 id 알아오기
			
			// 환불 테이블에 넣어줘야 함
			Map<String, String> paraMap = new HashMap<>();
			
			paraMap.put("fk_purchaseid", String.valueOf(fk_purchaseid)  );
			paraMap.put("refundbank", request.getParameter("refundbank"));
			paraMap.put("refundacc", request.getParameter("refundacc"));
			paraMap.put("accname", request.getParameter("accname"));

			purdao.insertRefund(paraMap);
			// System.out.println("환불 테이블 :"+m);
			
			
			
			
			/*
			// ===   >> SMS발송 << === //
			//   HashMap 에 받는사람번호, 보내는사람번호, 문자내용 등 을 저장한뒤 Coolsms 클래스의 send를 이용해 보냅니다.
			      
			//String api_key = "발급받은 본인의 API Key";  // 발급받은 본인 API Key
			String api_key = "NCSQDLMWNYJY9XJ2";
			
			// String api_secret = "발급받은 본인의 API Secret";  // 발급받은 본인 API Secret
			String api_secret = "5WMHUOM7E79YN1S9HY6DDLYT3BSN5A0D";  // 발급받은 본인 API Secret
			
			Message coolsms = new Message(api_key, api_secret);
		    // net.nurigo.java_sdk.api.Message 임. // @@@ import 주의!!!!
		    // 먼저 다운 받은  javaSDK-2.2.jar 를 /MyMVC/src/main/webapp/WEB-INF/lib/ 안에 넣어서  build 시켜야 함.
			
			
			if(!super.checkLogin(request)) { // 비회원주문
				smsContent = "[젠틀몬스터] \r\n 우리은행 1002-950-797783(예:김지현)으로 "+total+"원 입금해주세요.";
			} 
			
			
			// == 4개 파라미터(to, from, type, text)는 필수사항이다. == 
			HashMap<String, String> map = new HashMap<>();
			map.put("to", ovo.getMobile()); // 수신번호
		    // 2020년 10월 16일 이후로 발신번호 사전등록제로 인해 등록된 발신번호로만 문자를 보내실 수 있습니다
			map.put("from", "01027588339"); // 홈페이지에 등록한 본인 명의의 번호만 가능하다
			map.put("type", "SMS"); // Message type ( SMS(단문), LMS(장문), MMS, ATA )
			map.put("text", smsContent); // 문자내용
		      
		    // 꼭써야해!!!!
			map.put("app_version", "JAVA SDK v2.2"); // application name and version
		      
			coolsms.send(map);
		   // ===   >> SMS발송 끝 << === //
		   */
		   
			
			
			
		   // === 메일 발송하기 === //
		   // 라이브러리 다운받고 시큐리티 파일 고치고 메일보낼 준비가 됐다면 메일 보낼 클래스를 만들어서 사용할거임
			GoogleMail mail = new GoogleMail();
			
			// 메일이 성공적으로 보내졌는지 유무를 알아오기 위한 용도
			boolean sendMailSuccess = false;
			
			SimpleDateFormat dateft = new SimpleDateFormat("yyyy-MM-dd");
			Calendar currentDate = Calendar.getInstance();
			dateft.format(currentDate.getTime());
			
			String content = "";
			String subject = "[젠틀몬스터] 무통장 입금 안내 메일입니다" ;
			String title = ovo.getName() +"님의 주문번호 "+orderid+" 내역입니다.<br>"+ dateft.format(currentDate.getTime()) +" 밤 12시 이전까지 우리은행 1002-950-797783 (예금주: 김지현)으로 "+total+"원 입금해주셔야 주문이 완료 됩니다.";
			
			// 상품 수량 만큼 테이블에 추가해주기
			for(CartVO cvo : ordertList) {
				content+= "<tr><td><img src='http://127.0.0.1:9090/Genmon1/images/common/products/"+cvo.getAllProdvo().getPimage1()+"' style='width:100px;'/></td></tr>";
				content+= "<tr><td> 수량: "+cvo.getQty()+" 제품명: "+cvo.getAllProdvo().getParentProvo().getPname()+" "+cvo.getAllProdvo().getColorName()+"</td></tr>";
			} // end of for
			// System.out.println(content);
			try {
				mail.sendmail(ovo.getEmail(), content, title, subject); // 구글메일 클래스 안의 메일 보내기 메소드 사용
				sendMailSuccess =  true; // 메일전송이 성공했음을 기록함
				
			} catch (Exception e) { 
				// 메일전송이 실패한 경우 여기에 온다
				e.printStackTrace();
				sendMailSuccess = false; // 메일전송이 실패했음을 기록함
			}
			
			// System.out.println(sendMailSuccess);
			
			
			
			// 회원 장바구니 비워주기 
			if(super.checkLogin(request)) {
				
				InterCartDAO cdao = new CartDAO();
				int dc =cdao.deleteOrderedList(ordertList, ovo.getFk_userid());
				
				System.out.println(dc);
			}
			
			
			
			request.setAttribute("orderid", orderid);
			request.setAttribute("ordertList", ordertList);
			
			// 세션 다음 페이지에서 보여주고  버려줘야대 
			session.removeAttribute("ovo"); // 주문테이블
			session.removeAttribute("ordertList"); // 주문 상품목록
			
			
			super.setViewPage("/WEB-INF/jihyun/perchaseEnd.jsp");
		}
		
		
	}

}
