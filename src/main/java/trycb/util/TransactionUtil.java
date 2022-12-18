package trycb.util;

public class TransactionUtil {
    public static boolean isInTransaction() {
        // Not necessary, but may wish to include this check to confirm this is actually in a transaction.
//        TransactionalSupport.checkForTransactionInThreadLocalStorage().map((h) -> {
//            if (!h.isPresent())
//                throw new RuntimeException("not in transaction!");
//            return h;
//        });
        return false;
    }
}
