import org.ne81.sp.cmpp.CmppServer;

public class CmppTestServer {
	public static void main(String args[]) {
		System.out.println("run test");

		new CmppServer(17090).start();
	}
}
