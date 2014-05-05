import java.io.*;
import java.lang.Thread.*;
import java.util.concurrent.*;
import java.util.HashSet;

class constants {
    public static final int A = 0;
    public static final int Z = 25;
    public static final int num_letters = 26;
}

class TransactionAbortException extends Exception {}
// this is intended to be caught
class TransactionUsageError extends Error {}
// this is intended to be fatal
class InvalidTransactionError extends Error {}
// bad input; will have to skip this transaction

class Account {
    private int value = 0;
    private Thread writer = null;
    private HashSet<Thread> readers;

    public Account(int initialValue) {
        value = initialValue;
        readers = new HashSet<Thread>();
    }

    private void delay() {
        try {
            Thread.sleep(100);  // ms
        } catch(InterruptedException executor) {}
            // Java requires you to catch that
    }

    public int peek() {
        delay();
        Thread self = Thread.currentThread();
        synchronized (this) {
            if (writer == self || readers.contains(self)) {
                // should do all peeks before opening account
                // (but *can* peek while another thread has open)
                throw new TransactionUsageError();
            }
            return value;
        }
    }

    public void verify(int expectedValue)
        throws TransactionAbortException {
        delay();
        synchronized (this) {
            if (!readers.contains(Thread.currentThread())) {
                throw new TransactionUsageError();
            }
            if (value != expectedValue) {
                // somebody else modified value since we used it;
                // will have to retry
                throw new TransactionAbortException();
            }
        }
    }

    public void update(int newValue) {
        delay();
        synchronized (this) {
            if (writer != Thread.currentThread()) {
                throw new TransactionUsageError();
            }
            value = newValue;
        }
    }

    public void open(boolean forWriting)
        throws TransactionAbortException {
        delay();
        Thread self = Thread.currentThread();
        synchronized (this) {
            if (forWriting) {
                if (writer == self) {
                    throw new TransactionUsageError();
                }
                int numReaders = readers.size();
                if (writer != null || numReaders > 1
                        || (numReaders == 1 && !readers.contains(self))) {
                    // encountered conflict with another transaction;
                    // will have to retry
                    throw new TransactionAbortException();
                }
                writer = self;
            } else {
                if (readers.contains(self) || (writer == self)) {
                    throw new TransactionUsageError();
                }
                if (writer != null) {
                    // encountered conflict with another transaction;
                    // will have to retry
                    throw new TransactionAbortException();
                }
                readers.add(Thread.currentThread());
            }
        }
    }

    public void close() {
        delay();
        Thread self = Thread.currentThread();
        synchronized (this) {
            if (writer != self && !readers.contains(self)) {
                throw new TransactionUsageError();
            }
            if (writer == self) writer = null;
            if (readers.contains(self)) readers.remove(self);
        }
    }

    // print value in wide output field
    public void print() {
        System.out.format("%11d", new Integer(value));
    }

    // print value % num_letters (indirection value) in 2 columns
    public void printMod() {
        int val = value % constants.num_letters;
        if (val < 10) System.out.print("0");
        System.out.print(val);
    }
}

class Worker
{
  private static final int A = constants.A;
  private static final int Z = constants.Z;
  private static final int num_letters = constants.num_letters;

  private Account[] accounts;
  private String transaction;

  // Keep data about open, reading, writing on each account
  int[] cache = new int[num_letters];
  boolean[] read_accounts = new boolean[num_letters];
  boolean[] write_accounts = new boolean[num_letters];
  boolean[] open_accounts = new boolean[num_letters];

  private void resetAccounts()
  {
    // Initialize starting values for all accounts
    for (int i = 0; i < num_letters; ++i)
    {
      read_accounts[i] = false;
      open_accounts[i] = false;
      write_accounts[i] = false;
      cache[i] = 0;
    }
  }

  public Worker(Account[] allAccounts, String trans)
  {
    accounts = allAccounts;
    transaction = trans;
    resetAccounts();
  }

  // Find account number and return as int
  private int parseAccount(String name)
  {
    int account_number = (int) (name.charAt(0)) - (int) 'A';

    if (account_number < A || account_number > Z)
    {
      throw new InvalidTransactionError();
    }

    for (int i = 1; i < name.length(); ++i)
    {
      if (name.charAt(i) != '*')
      {
        throw new InvalidTransactionError();
      }

      account_number = (accounts[account_number].peek() % num_letters);
    }

    // Return index of account as opposed to reference (working with indices)
    return account_number;
  }

  // Return cached account value or number and set flag
  private int parseAccountOrNum(String name)
  {
    int rtn = 0;

    if (name.charAt(0) >= '0' && name.charAt(0) <= '9')
    {
      rtn = new Integer(name).intValue();
    }
    else
    {
      rtn = parseAccount(name);

      try
      {
        cache[rtn] = accounts[rtn].peek();
        read_accounts[rtn] = true;
        rtn = cache[rtn];
      }
      catch (TransactionUsageError transaction_usage_error)
      {
        System.err.println("failure to peek at parseAccountOrNum");
      }
    }

    return rtn;
  }

  private void tryOpeningAccounts() throws TransactionAbortException
  {
    // Open accounts for reading & writing
    for (int k = 0; k < num_letters; k++)
    {
      if (read_accounts[k])
      {
        if (write_accounts[k])
        {
          accounts[k].open(true);
          open_accounts[k] = true;
        }
        else
        {
          accounts[k].open(false);
          open_accounts[k] = true;
          accounts[k].verify(cache[k]);
        }
      }
    }
  }

  private void closeOpenAccounts(boolean close_write)
  {
    // Close down all open accounts
    for (int k = 0; k < num_letters; k++)
    {
      if (open_accounts[k])
      {
        accounts[k].close();
        open_accounts[k] = false;

        // Check if we are closing the write account also
        if (close_write)
        {
          write_accounts[k] = false;
        }

      }
    }
  }

  public void run()
  {
    // Tokenize transaction
    String[] commands = transaction.split(";");

    for (int i = 0; i < commands.length; ++i)
    {
      String[] words = commands[i].trim().split("\\s");

      if (words.length < 3)
      {
        throw new InvalidTransactionError();
      }

      // Flag to signal transaction quit
      int transaction_quit = 1;

      // Init lhs (acct to be modified) and rhs (0 for safety)
      int rhs = 0;
      int lhs = parseAccount(words[0]);
      write_accounts[lhs] = true;

      while (transaction_quit == 1)
      {
        transaction_quit = 0;
        // Peek at lhs, cache, and denote as readable
        try
        {
          cache[lhs] = accounts[lhs].peek();
          read_accounts[lhs] = true;
        }
        catch (TransactionUsageError tue)
        {
          System.err.println("Failure to peek on lhs");
        }

        // Check syntax of transaction for correctness
        if (!words[1].equals("="))
        {
          throw new InvalidTransactionError();
        }

        // Get account number after equals sign in transaction
        rhs = parseAccountOrNum(words[2]);

        // Check operations
        for (int j = 3; j < words.length; j+=2)
        {
          if (words[j].equals("+"))
            rhs += parseAccountOrNum(words[j+1]);
          else if (words[j].equals("-"))
            rhs -= parseAccountOrNum(words[j+1]);
          else
            throw new InvalidTransactionError();
        }

        try
        {
          tryOpeningAccounts();
        }
        catch (TransactionAbortException transaction_abort_exception)
        {
          // Set transaction quit because we have failed verification
          transaction_quit = 1;
          // No need to close write accounts
          closeOpenAccounts(false);
        }
      }

      // Try to write determined value to lhs
      try
      {
        accounts[lhs].update(rhs);
      }
      catch (TransactionUsageError tue)
      {
        System.err.println("Failure to update account");
      }

      // Close down all open accounts (write included)
      closeOpenAccounts(true);
    }

    System.out.println("commit: " + transaction);
  }
}

public class ServerParallel
{
  private static final int A = constants.A;
  private static final int Z = constants.Z;
  private static final int num_letters = constants.num_letters;
  private static Account[] accounts;

  // Initialize ExecutorService using newCachedThreadPool threading algo
  private static ExecutorService executor = Executors.newCachedThreadPool();

  private static void dumpAccounts() {
    // output values:
    for (int i = A; i <= Z; ++i) {
      System.out.print("    ");
      if (i < 10) System.out.print("0");
      System.out.print(i + " ");
      System.out.print(new Character((char) (i + 'A')) + ": ");
      accounts[i].print();
      System.out.print(" (");
      accounts[i].printMod();
      System.out.print(")\n");
    }
  }

  public static void main (String args[]) throws IOException
  {
    accounts = new Account[num_letters];
    for (int i = A; i <= Z; ++i)
    {
      accounts[i] = new Account(Z-i);
    }

    // Read transactions from input file
    String line;
    BufferedReader input = new BufferedReader(new FileReader(args[0]));

    // Begin attempt to execute concurrent transactions
    while ((line = input.readLine()) != null)
    {

      // Make 'final' version of line to pass into inner runnable
      final String final_line = line;

      // Container class for worker run
      Runnable worker_runnable = new Runnable()
      {
          public void run()
          {
              try
              {
                  Worker w = new Worker(accounts, final_line);
                  w.run();
              }
              catch (InvalidTransactionError invalid_transaction_error)
              {
                  System.err.println(final_line + " created an invalid transaction error!");
              }
          }
      };

      executor.execute(worker_runnable);
    }

    // Cleanly kill threadpool when finished with transactions
    executor.shutdown();

    // Check up on tasks and try to wait for them to finish
    try
    {
      // Give current task decent time to finish execution
      if (!executor.awaitTermination(30, TimeUnit.SECONDS))
      {
        // Force shut down if task takes too long
        executor.shutdownNow();

        // If shut down, wait for task to be properly cleared
        if (!executor.awaitTermination(15, TimeUnit.SECONDS))
        {
          System.err.println("Executor never shut down");
        }
      }
    }
    catch (InterruptedException interrupted_exception)
    {
      // Force shut down task if it is interrupted
      executor.shutdownNow();
    }

    // Each task has either successfully or unsuccessfully executed
    // Proceed to print of final values
    System.out.println("final values:");
    dumpAccounts();
  }
}
