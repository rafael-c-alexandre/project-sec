package proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.33.1)",
    comments = "Source: ClientToServer.proto")
public final class ServerGrpc {

  private ServerGrpc() {}

  public static final String SERVICE_NAME = "proto.Server";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<proto.SubmitLocationReportRequest,
      proto.SubmitLocationReportReply> getSubmitLocationReportMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SubmitLocationReport",
      requestType = proto.SubmitLocationReportRequest.class,
      responseType = proto.SubmitLocationReportReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<proto.SubmitLocationReportRequest,
      proto.SubmitLocationReportReply> getSubmitLocationReportMethod() {
    io.grpc.MethodDescriptor<proto.SubmitLocationReportRequest, proto.SubmitLocationReportReply> getSubmitLocationReportMethod;
    if ((getSubmitLocationReportMethod = ServerGrpc.getSubmitLocationReportMethod) == null) {
      synchronized (ServerGrpc.class) {
        if ((getSubmitLocationReportMethod = ServerGrpc.getSubmitLocationReportMethod) == null) {
          ServerGrpc.getSubmitLocationReportMethod = getSubmitLocationReportMethod =
              io.grpc.MethodDescriptor.<proto.SubmitLocationReportRequest, proto.SubmitLocationReportReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SubmitLocationReport"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  proto.SubmitLocationReportRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  proto.SubmitLocationReportReply.getDefaultInstance()))
              .setSchemaDescriptor(new ServerMethodDescriptorSupplier("SubmitLocationReport"))
              .build();
        }
      }
    }
    return getSubmitLocationReportMethod;
  }

  private static volatile io.grpc.MethodDescriptor<proto.ObtainLocationReportRequest,
      proto.ObtainLocationReportReply> getObtainLocationReportMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ObtainLocationReport",
      requestType = proto.ObtainLocationReportRequest.class,
      responseType = proto.ObtainLocationReportReply.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<proto.ObtainLocationReportRequest,
      proto.ObtainLocationReportReply> getObtainLocationReportMethod() {
    io.grpc.MethodDescriptor<proto.ObtainLocationReportRequest, proto.ObtainLocationReportReply> getObtainLocationReportMethod;
    if ((getObtainLocationReportMethod = ServerGrpc.getObtainLocationReportMethod) == null) {
      synchronized (ServerGrpc.class) {
        if ((getObtainLocationReportMethod = ServerGrpc.getObtainLocationReportMethod) == null) {
          ServerGrpc.getObtainLocationReportMethod = getObtainLocationReportMethod =
              io.grpc.MethodDescriptor.<proto.ObtainLocationReportRequest, proto.ObtainLocationReportReply>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ObtainLocationReport"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  proto.ObtainLocationReportRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  proto.ObtainLocationReportReply.getDefaultInstance()))
              .setSchemaDescriptor(new ServerMethodDescriptorSupplier("ObtainLocationReport"))
              .build();
        }
      }
    }
    return getObtainLocationReportMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ServerStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ServerStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ServerStub>() {
        @java.lang.Override
        public ServerStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ServerStub(channel, callOptions);
        }
      };
    return ServerStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ServerBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ServerBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ServerBlockingStub>() {
        @java.lang.Override
        public ServerBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ServerBlockingStub(channel, callOptions);
        }
      };
    return ServerBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ServerFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ServerFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ServerFutureStub>() {
        @java.lang.Override
        public ServerFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ServerFutureStub(channel, callOptions);
        }
      };
    return ServerFutureStub.newStub(factory, channel);
  }

  /**
   */
  public static abstract class ServerImplBase implements io.grpc.BindableService {

    /**
     */
    public void submitLocationReport(proto.SubmitLocationReportRequest request,
        io.grpc.stub.StreamObserver<proto.SubmitLocationReportReply> responseObserver) {
      asyncUnimplementedUnaryCall(getSubmitLocationReportMethod(), responseObserver);
    }

    /**
     */
    public void obtainLocationReport(proto.ObtainLocationReportRequest request,
        io.grpc.stub.StreamObserver<proto.ObtainLocationReportReply> responseObserver) {
      asyncUnimplementedUnaryCall(getObtainLocationReportMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getSubmitLocationReportMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                proto.SubmitLocationReportRequest,
                proto.SubmitLocationReportReply>(
                  this, METHODID_SUBMIT_LOCATION_REPORT)))
          .addMethod(
            getObtainLocationReportMethod(),
            asyncUnaryCall(
              new MethodHandlers<
                proto.ObtainLocationReportRequest,
                proto.ObtainLocationReportReply>(
                  this, METHODID_OBTAIN_LOCATION_REPORT)))
          .build();
    }
  }

  /**
   */
  public static final class ServerStub extends io.grpc.stub.AbstractAsyncStub<ServerStub> {
    private ServerStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServerStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ServerStub(channel, callOptions);
    }

    /**
     */
    public void submitLocationReport(proto.SubmitLocationReportRequest request,
        io.grpc.stub.StreamObserver<proto.SubmitLocationReportReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getSubmitLocationReportMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void obtainLocationReport(proto.ObtainLocationReportRequest request,
        io.grpc.stub.StreamObserver<proto.ObtainLocationReportReply> responseObserver) {
      asyncUnaryCall(
          getChannel().newCall(getObtainLocationReportMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   */
  public static final class ServerBlockingStub extends io.grpc.stub.AbstractBlockingStub<ServerBlockingStub> {
    private ServerBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServerBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ServerBlockingStub(channel, callOptions);
    }

    /**
     */
    public proto.SubmitLocationReportReply submitLocationReport(proto.SubmitLocationReportRequest request) {
      return blockingUnaryCall(
          getChannel(), getSubmitLocationReportMethod(), getCallOptions(), request);
    }

    /**
     */
    public proto.ObtainLocationReportReply obtainLocationReport(proto.ObtainLocationReportRequest request) {
      return blockingUnaryCall(
          getChannel(), getObtainLocationReportMethod(), getCallOptions(), request);
    }
  }

  /**
   */
  public static final class ServerFutureStub extends io.grpc.stub.AbstractFutureStub<ServerFutureStub> {
    private ServerFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ServerFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ServerFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<proto.SubmitLocationReportReply> submitLocationReport(
        proto.SubmitLocationReportRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getSubmitLocationReportMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<proto.ObtainLocationReportReply> obtainLocationReport(
        proto.ObtainLocationReportRequest request) {
      return futureUnaryCall(
          getChannel().newCall(getObtainLocationReportMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SUBMIT_LOCATION_REPORT = 0;
  private static final int METHODID_OBTAIN_LOCATION_REPORT = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final ServerImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(ServerImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SUBMIT_LOCATION_REPORT:
          serviceImpl.submitLocationReport((proto.SubmitLocationReportRequest) request,
              (io.grpc.stub.StreamObserver<proto.SubmitLocationReportReply>) responseObserver);
          break;
        case METHODID_OBTAIN_LOCATION_REPORT:
          serviceImpl.obtainLocationReport((proto.ObtainLocationReportRequest) request,
              (io.grpc.stub.StreamObserver<proto.ObtainLocationReportReply>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class ServerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ServerBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return proto.ServerProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("Server");
    }
  }

  private static final class ServerFileDescriptorSupplier
      extends ServerBaseDescriptorSupplier {
    ServerFileDescriptorSupplier() {}
  }

  private static final class ServerMethodDescriptorSupplier
      extends ServerBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    ServerMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ServerGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ServerFileDescriptorSupplier())
              .addMethod(getSubmitLocationReportMethod())
              .addMethod(getObtainLocationReportMethod())
              .build();
        }
      }
    }
    return result;
  }
}
