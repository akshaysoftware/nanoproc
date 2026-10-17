/** A one-shot child process that echoes stdin until the parent closes it. */
public final class Echo {
  public static void main(String[] args) throws Exception {
    System.in.transferTo(System.out);
  }
}
