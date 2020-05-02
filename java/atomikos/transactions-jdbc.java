// 隔段时间可能会报错，已转向bitronix.tm
package our.weld.utility;

import com.atomikos.icatch.CompositeTransactionManager;
import com.atomikos.icatch.config.Configuration;
import com.atomikos.icatch.jta.UserTransactionImp;
import com.atomikos.icatch.jta.UserTransactionManager;
import javax.transaction.RollbackException;
import javax.transaction.Synchronization;
import javax.transaction.UserTransaction;
import javax.transaction.Status;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

import org.jboss.weld.transaction.spi.TransactionServices;

/**
 *
 * @author xiaodong
 */
//  参考 - https://github.com/microbean/microbean-narayana-jta-weld-se/blob/master/src/main/java/org/microbean/narayana/jta/weld/se/NarayanaTransactionServices.java
//  注意 - 似乎实际运行中，并未调用registerSynchronization(...)和isTransactionActive(...)
public class MyTransactionServices implements TransactionServices {

    private final TransactionManager txMgr;
    private final UserTransaction userTx;

    public MyTransactionServices() {
        System.err.println("MyTransactionServices - init");

        UserTransactionManager utm = null;
        CompositeTransactionManager ctm = Configuration.getCompositeTransactionManager();
        if (ctm == null) {
            utm = new UserTransactionManager();
            try {
                //  初始化TransactionManager,避免出现 - WARNING: transaction manager not running?
                utm.init();
            } catch (SystemException se) {
                System.err.println("!!!!!!!!!!!!!!!!!!!!!-" + se);
                //utm.close();
            }

            System.err.println("CompositeTransactionManager - " + ctm);
        }

        this.txMgr = utm;
        this.userTx = new UserTransactionImp();
    }

    @Override
    public UserTransaction getUserTransaction() {
        return this.userTx;//new com.atomikos.icatch.jta.UserTransactionImp();
    }

    @Override
    public void registerSynchronization(Synchronization synchronizedObserver) {
        System.out.println("registerSynchronization");
        try {
            this.txMgr.getTransaction().registerSynchronization(synchronizedObserver);
        } catch (SystemException | RollbackException | IllegalStateException ex) {
            System.err.println(ex);
        }
    }

    @Override
    public boolean isTransactionActive() {

        int status = 0;
        try {
            status = getUserTransaction().getStatus();
        } catch (final SystemException e) {
            System.err.println(e);
        }

        boolean temp = status == Status.STATUS_ACTIVE
                || status == Status.STATUS_COMMITTING
                || status == Status.STATUS_MARKED_ROLLBACK
                || status == Status.STATUS_PREPARED
                || status == Status.STATUS_PREPARING
                || status == Status.STATUS_ROLLING_BACK;

        System.out.println("isTransactionActive - " + temp);

        return temp;

//        try {
//            return getUserTransaction().getStatus() == Status.STATUS_ACTIVE;
//        } catch (SystemException ex) {
//            System.err.println(ex);
//            return false;
//        }
    }

    @Override
    public void cleanup() {
    }
}
