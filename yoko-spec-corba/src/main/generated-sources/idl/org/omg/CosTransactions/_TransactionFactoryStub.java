package org.omg.CosTransactions;


/**
* org/omg/CosTransactions/_TransactionFactoryStub.java .
* Error reading Messages File.
* Error reading Messages File.
* Thursday, January 14, 2010 1:08:59 AM PST
*/

public class _TransactionFactoryStub extends org.omg.CORBA.portable.ObjectImpl implements org.omg.CosTransactions.TransactionFactory
{

  public org.omg.CosTransactions.Control create (int time_out)
  {
            org.omg.CORBA.portable.InputStream $in = null;
            try {
                org.omg.CORBA.portable.OutputStream $out = _request ("create", true);
                $out.write_ulong (time_out);
                $in = _invoke ($out);
                org.omg.CosTransactions.Control $result = org.omg.CosTransactions.ControlHelper.read ($in);
                return $result;
            } catch (org.omg.CORBA.portable.ApplicationException $ex) {
                $in = $ex.getInputStream ();
                String _id = $ex.getId ();
                throw new org.omg.CORBA.MARSHAL (_id);
            } catch (org.omg.CORBA.portable.RemarshalException $rm) {
                return create (time_out        );
            } finally {
                _releaseReply ($in);
            }
  } // create

  public org.omg.CosTransactions.Control recreate (org.omg.CosTransactions.PropagationContext ctx)
  {
            org.omg.CORBA.portable.InputStream $in = null;
            try {
                org.omg.CORBA.portable.OutputStream $out = _request ("recreate", true);
                org.omg.CosTransactions.PropagationContextHelper.write ($out, ctx);
                $in = _invoke ($out);
                org.omg.CosTransactions.Control $result = org.omg.CosTransactions.ControlHelper.read ($in);
                return $result;
            } catch (org.omg.CORBA.portable.ApplicationException $ex) {
                $in = $ex.getInputStream ();
                String _id = $ex.getId ();
                throw new org.omg.CORBA.MARSHAL (_id);
            } catch (org.omg.CORBA.portable.RemarshalException $rm) {
                return recreate (ctx        );
            } finally {
                _releaseReply ($in);
            }
  } // recreate

  // Type-specific CORBA::Object operations
  private static String[] __ids = {
    "IDL:CosTransactions/TransactionFactory:1.0"};

  public String[] _ids ()
  {
    return (String[])__ids.clone ();
  }

  private void readObject (java.io.ObjectInputStream s) throws java.io.IOException
  {
     String str = s.readUTF ();
     String[] args = null;
     java.util.Properties props = null;
     org.omg.CORBA.Object obj = org.omg.CORBA.ORB.init (args, props).string_to_object (str);
     org.omg.CORBA.portable.Delegate delegate = ((org.omg.CORBA.portable.ObjectImpl) obj)._get_delegate ();
     _set_delegate (delegate);
  }

  private void writeObject (java.io.ObjectOutputStream s) throws java.io.IOException
  {
     String[] args = null;
     java.util.Properties props = null;
     String str = org.omg.CORBA.ORB.init (args, props).object_to_string (this);
     s.writeUTF (str);
  }
} // class _TransactionFactoryStub
