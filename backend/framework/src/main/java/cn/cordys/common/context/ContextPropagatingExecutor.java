package cn.cordys.common.context;

import java.util.concurrent.Executor;

/**
 * 在 {@link Executor#execute} 提交时捕获调用线程上下文，在 worker 线程恢复并清理。
 */
public final class ContextPropagatingExecutor implements Executor {

    private final Executor delegate;

    private ContextPropagatingExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    public static Executor wrap(Executor delegate) {
        if (delegate instanceof ContextPropagatingExecutor) {
            return delegate;
        }
        return new ContextPropagatingExecutor(delegate);
    }

    @Override
    public void execute(Runnable command) {
        ContextPropagation.Snapshot snapshot = ContextPropagation.capture();
        delegate.execute(ContextPropagation.wrap(command, snapshot));
    }
}
